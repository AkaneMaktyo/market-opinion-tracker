package com.personal.tracker.service.youtube;

import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.repository.youtube.YouTubeRepository;
import com.personal.tracker.repository.youtube.YouTubeRepository.ChannelRecord;
import com.personal.tracker.repository.youtube.YouTubeRepository.SaveChannelCommand;
import com.personal.tracker.repository.youtube.YouTubeRepository.SaveVideoCommand;
import com.personal.tracker.repository.youtube.YouTubeRepository.TranscriptSegment;
import com.personal.tracker.repository.youtube.YouTubeRepository.VideoRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class YouTubeAdminService {
  static final String TRANSCRIPT_READY = "ready";
  static final String TRANSCRIPT_ERROR = "error";
  static final String TRANSCRIPT_RETRY_MIDNIGHT = "retry_midnight";
  private final YouTubeRepository repository;
  private final YouTubeClient client;
  private final YouTubeAudioDownloader downloader;
  private final AliyunOssClient ossClient;
  private final AliyunSpeechTranscriber transcriber;
  private final YouTubeTranscriptNotifier notifier;
  private final int maxVideos;
  private final int retryBatchLimit;

  @Autowired
  public YouTubeAdminService(
      YouTubeRepository repository,
      YouTubeClient client,
      YouTubeAudioDownloader downloader,
      AliyunOssClient ossClient,
      AliyunSpeechTranscriber transcriber,
      YouTubeTranscriptNotifier notifier,
      Environment environment) {
    this(
        repository,
        client,
        downloader,
        ossClient,
        transcriber,
        notifier,
        maxVideos(environment),
        retryBatchLimit(environment));
  }

  YouTubeAdminService(
      YouTubeRepository repository,
      YouTubeClient client,
      YouTubeAudioDownloader downloader,
      AliyunOssClient ossClient,
      AliyunSpeechTranscriber transcriber,
      YouTubeTranscriptNotifier notifier,
      int maxVideos,
      int retryBatchLimit) {
    this.repository = repository;
    this.client = client;
    this.downloader = downloader;
    this.ossClient = ossClient;
    this.transcriber = transcriber;
    this.notifier = notifier;
    this.maxVideos = Math.max(1, maxVideos);
    this.retryBatchLimit = Math.max(1, retryBatchLimit);
  }

  public List<DashboardChannel> listDashboard() {
    return repository.listChannels().stream()
        .map(channel -> new DashboardChannel(
            channel,
            repository.listVideos(channel.id(), 8).stream().map(this::summaryVideo).toList()))
        .toList();
  }

  public SyncResult addChannel(String sourceUrl, String name) {
    var resolved = client.resolveChannel(sourceUrl);
    ChannelRecord saved = repository.saveChannel(new SaveChannelCommand(
        resolved.channelId(),
        name == null || name.isBlank() ? resolved.title() : name.trim(),
        resolved.handle(),
        resolved.sourceUrl(),
        true,
        "",
        "",
        now()));
    return syncChannel(saved.id());
  }

  public SyncResult syncChannel(String channelRowId) {
    ChannelRecord channel = repository.findChannel(channelRowId)
        .orElseThrow(() -> new IllegalArgumentException("频道不存在"));
    return syncFetchedChannel(channel, client.listVideos(channel.channelId(), maxVideos));
  }

  public List<SyncResult> syncAll() {
    return repository.listChannels().stream()
        .filter(ChannelRecord::enabled)
        .map(channel -> syncChannel(channel.id()))
        .toList();
  }

  public AutoSyncSummary syncUpdatedChannels() {
    int checked = 0;
    int updated = 0;
    int processedVideos = 0;
    for (ChannelRecord channel : repository.listChannels()) {
      if (!channel.enabled()) {
        continue;
      }
      checked++;
      List<YouTubeClient.VideoMetadata> candidates = client.listVideos(channel.channelId(), maxVideos);
      if (!hasNewVideo(channel, candidates)) {
        touchChannel(channel);
        continue;
      }
      SyncResult result = syncFetchedChannel(channel, candidates);
      updated++;
      processedVideos += result.videos().size();
    }
    return new AutoSyncSummary(checked, updated, processedVideos);
  }

  public int retryQuotaLimitedVideos() {
    int retried = 0;
    for (VideoRecord video : repository.listByTranscriptStatus(TRANSCRIPT_RETRY_MIDNIGHT, retryBatchLimit)) {
      retryQuotaLimitedVideo(video);
      retried++;
    }
    return retried;
  }

  public boolean deleteChannel(String channelRowId) {
    return repository.deleteChannel(channelRowId);
  }

  public VideoRecord getVideo(String videoId) {
    return repository.findVideo(videoId).orElse(null);
  }

  public VideoRecord ensureAudio(String videoId) {
    VideoRecord video = repository.findVideo(videoId).orElse(null);
    if (video == null) {
      return null;
    }
    Path localPath = audioPath(video.audioPath());
    if (localPath != null && Files.exists(localPath)) {
      return video;
    }
    if (video.videoUrl() == null || video.videoUrl().isBlank()) {
      return video;
    }
    var audio = downloader.download(video.videoId(), video.videoUrl());
    return repository.saveVideo(new SaveVideoCommand(
        video.videoId(),
        video.channelRowId(),
        video.channelId(),
        video.title(),
        video.videoUrl(),
        video.publishedAt(),
        audio.audioPath(),
        audio.audioDurationMs() > 0 ? audio.audioDurationMs() : video.audioDurationMs(),
        video.transcriptStatus(),
        video.transcriptLanguage(),
        video.transcriptSource(),
        video.transcriptText(),
        video.transcriptSegments(),
        video.errorMessage(),
        video.syncedAt(),
        now()));
  }

  public String getCloudAudioLink(String videoId) {
    VideoRecord video = repository.findVideo(videoId).orElse(null);
    if (video == null || !"aliyun_filetrans".equals(video.transcriptSource())) {
      return "";
    }
    String stored = ossClient.signStoredAudio(video.audioPath());
    return stored == null || stored.isBlank() ? ossClient.signVideoAudio(videoId) : stored;
  }

  private SyncResult syncFetchedChannel(
      ChannelRecord channel,
      List<YouTubeClient.VideoMetadata> candidates) {
    String nextLatest = nextLatest(channel.lastVideoPublishedAt(), candidates);
    List<VideoRecord> videos = new ArrayList<>();
    for (YouTubeClient.VideoMetadata video : candidates) {
      videos.add(syncVideo(channel, video));
    }
    ChannelRecord updated = repository.saveChannel(new SaveChannelCommand(
        channel.channelId(),
        channel.title(),
        channel.handle(),
        channel.sourceUrl(),
        channel.enabled(),
        now(),
        nextLatest,
        now()));
    return new SyncResult(updated, videos);
  }

  private VideoRecord syncVideo(ChannelRecord channel, YouTubeClient.VideoMetadata video) {
    VideoRecord existing = repository.findVideo(video.videoId()).orElse(null);
    if (hasReadyTranscript(existing)) {
      return existing;
    }
    VideoRecord base = existing == null
        ? baseVideo(channel, video.videoId(), video.title(), video.videoUrl(), video.publishedAt())
        : existing;
    var audio = downloader.download(video.videoId(), video.videoUrl());
    return transcribeAndSave(base, channel, audio.audioPath(), audio.audioDurationMs(), now());
  }

  private boolean hasNewVideo(
      ChannelRecord channel,
      List<YouTubeClient.VideoMetadata> candidates) {
    if (candidates.isEmpty()) {
      return false;
    }
    String latest = nextLatest(channel.lastVideoPublishedAt(), candidates);
    String current = channel.lastVideoPublishedAt() == null ? "" : channel.lastVideoPublishedAt();
    return !latest.isBlank() && latest.compareTo(current) > 0;
  }

  private void touchChannel(ChannelRecord channel) {
    repository.saveChannel(new SaveChannelCommand(
        channel.channelId(),
        channel.title(),
        channel.handle(),
        channel.sourceUrl(),
        channel.enabled(),
        now(),
        channel.lastVideoPublishedAt(),
        now()));
  }

  private void retryQuotaLimitedVideo(VideoRecord queued) {
    ChannelRecord channel = repository.findChannel(queued.channelRowId()).orElse(baseChannel(queued));
    VideoRecord hydrated = ensureAudio(queued.videoId());
    if (hydrated == null || hydrated.audioPath() == null || hydrated.audioPath().isBlank()) {
      saveVideo(
          queued,
          queued.audioPath(),
          queued.audioDurationMs(),
          TRANSCRIPT_ERROR,
          queued.transcriptLanguage(),
          queued.transcriptSource(),
          queued.transcriptText(),
          queued.transcriptSegments(),
          "阿里云午夜重试失败：音频文件不存在");
      return;
    }
    transcribeAndSave(hydrated, channel, hydrated.audioPath(), hydrated.audioDurationMs(), now());
  }

  private VideoRecord transcribeAndSave(
      VideoRecord base,
      ChannelRecord channel,
      String audioPath,
      long audioDurationMs,
      String syncedAt) {
    try {
      AliyunSpeechTranscriber.TranscriptResult transcript = transcriber.transcribe(audioPath);
      VideoRecord saved = saveVideo(
          base,
          audioPath,
          audioDurationMs,
          TRANSCRIPT_READY,
          transcript.transcriptLanguage(),
          transcript.transcriptSource(),
          transcript.transcriptText(),
          transcript.transcriptSegments(),
          transcript.errorMessage(),
          syncedAt);
      notifier.notifyTranscriptReady(channel, saved);
      return saved;
    } catch (Exception error) {
      if (AliyunFileTransClient.isQuotaExceeded(error)) {
        return saveVideo(
            base,
            audioPath,
            audioDurationMs,
            TRANSCRIPT_RETRY_MIDNIGHT,
            "zh",
            "aliyun_filetrans",
            "",
            List.of(),
            "阿里云识别配额不足，已安排在 0 点自动重试",
            syncedAt);
      }
      return saveVideo(
          base,
          audioPath,
          audioDurationMs,
          TRANSCRIPT_ERROR,
          "",
          "",
          "",
          List.of(),
          error.getMessage(),
          syncedAt);
    }
  }

  private VideoRecord saveVideo(
      VideoRecord base,
      String audioPath,
      long audioDurationMs,
      String transcriptStatus,
      String transcriptLanguage,
      String transcriptSource,
      String transcriptText,
      List<TranscriptSegment> transcriptSegments,
      String errorMessage) {
    return saveVideo(
        base,
        audioPath,
        audioDurationMs,
        transcriptStatus,
        transcriptLanguage,
        transcriptSource,
        transcriptText,
        transcriptSegments,
        errorMessage,
        now());
  }

  private VideoRecord saveVideo(
      VideoRecord base,
      String audioPath,
      long audioDurationMs,
      String transcriptStatus,
      String transcriptLanguage,
      String transcriptSource,
      String transcriptText,
      List<TranscriptSegment> transcriptSegments,
      String errorMessage,
      String syncedAt) {
    return repository.saveVideo(new SaveVideoCommand(
        base.videoId(),
        base.channelRowId(),
        base.channelId(),
        base.title(),
        base.videoUrl(),
        base.publishedAt(),
        audioPath == null ? "" : audioPath,
        Math.max(0, audioDurationMs),
        transcriptStatus,
        transcriptLanguage == null ? "" : transcriptLanguage,
        transcriptSource == null ? "" : transcriptSource,
        transcriptText == null ? "" : transcriptText,
        transcriptSegments == null ? List.of() : transcriptSegments,
        errorMessage == null ? "" : errorMessage,
        syncedAt,
        now()));
  }

  private static boolean hasReadyTranscript(VideoRecord video) {
    if (video == null || !TRANSCRIPT_READY.equals(video.transcriptStatus())) {
      return false;
    }
    if (video.transcriptSegments() != null && !video.transcriptSegments().isEmpty()) {
      return true;
    }
    return video.transcriptText() != null && !video.transcriptText().isBlank();
  }

  private VideoRecord summaryVideo(VideoRecord video) {
    return new VideoRecord(
        video.videoId(),
        video.channelRowId(),
        video.channelId(),
        video.title(),
        video.videoUrl(),
        video.publishedAt(),
        video.audioPath(),
        video.audioDurationMs(),
        video.transcriptStatus(),
        video.transcriptLanguage(),
        video.transcriptSource(),
        "",
        List.of(),
        video.errorMessage(),
        video.syncedAt(),
        video.createdAt(),
        video.updatedAt());
  }

  private static Path audioPath(String rawPath) {
    if (rawPath == null || rawPath.isBlank()) {
      return null;
    }
    return Path.of(rawPath.replace("\\", "/"));
  }

  private static int maxVideos(Environment environment) {
    String raw = environment.getProperty("YOUTUBE_SYNC_MAX_VIDEOS");
    if (raw == null || raw.isBlank()) {
      return 1;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException error) {
      return 1;
    }
  }

  private static int retryBatchLimit(Environment environment) {
    String raw = environment.getProperty("YOUTUBE_RETRY_BATCH_LIMIT");
    if (raw == null || raw.isBlank()) {
      return 20;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException error) {
      return 20;
    }
  }

  private static String nextLatest(
      String currentLatest,
      List<YouTubeClient.VideoMetadata> candidates) {
    String nextLatest = currentLatest == null ? "" : currentLatest;
    for (YouTubeClient.VideoMetadata video : candidates) {
      String publishedAt = video.publishedAt() == null ? "" : video.publishedAt();
      if (publishedAt.compareTo(nextLatest) > 0) {
        nextLatest = publishedAt;
      }
    }
    return nextLatest;
  }

  private static VideoRecord baseVideo(
      ChannelRecord channel,
      String videoId,
      String title,
      String videoUrl,
      String publishedAt) {
    return new VideoRecord(
        videoId,
        channel.id(),
        channel.channelId(),
        title,
        videoUrl,
        publishedAt,
        "",
        0,
        "",
        "",
        "",
        "",
        List.of(),
        "",
        "",
        "",
        "");
  }

  private static ChannelRecord baseChannel(VideoRecord video) {
    return new ChannelRecord(
        video.channelRowId(),
        video.channelId(),
        video.channelId(),
        "",
        "",
        true,
        "",
        "",
        video.createdAt(),
        video.updatedAt());
  }

  private static String now() {
    return JdbcSupport.now();
  }

  public record DashboardChannel(ChannelRecord channel, List<VideoRecord> videos) {
  }

  public record SyncResult(ChannelRecord channel, List<VideoRecord> videos) {
  }

  public record AutoSyncSummary(int checkedChannels, int updatedChannels, int processedVideos) {
  }
}
