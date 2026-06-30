package com.personal.tracker.service.youtube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.repository.youtube.YouTubeRepository;
import com.personal.tracker.repository.youtube.YouTubeRepository.ChannelRecord;
import com.personal.tracker.repository.youtube.YouTubeRepository.SaveVideoCommand;
import com.personal.tracker.repository.youtube.YouTubeRepository.TranscriptSegment;
import com.personal.tracker.repository.youtube.YouTubeRepository.VideoRecord;
import com.personal.tracker.service.notify.WxPusherPushClient;
import com.personal.tracker.service.youtube.opinion.YouTubeOpinionAutoImportService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class YouTubeAdminServiceTest {
  @Test
  void listDashboardOmitsHeavyTranscriptPayloads() {
    var repository = mock(YouTubeRepository.class);
    var service = service(repository, mock(YouTubeClient.class), mock(YouTubeAudioDownloader.class));
    ChannelRecord channel = channel("2026-06-12T08:00:00Z");
    VideoRecord video = new VideoRecord(
        "vid-1",
        "channel-row",
        "channel-id",
        "Video",
        "https://example.com/watch?v=1",
        "2026-06-12T08:00:00Z",
        "audio.m4a",
        1234,
        YouTubeAdminService.TRANSCRIPT_READY,
        "zh",
        "aliyun_filetrans",
        "完整转写正文",
        List.of(new TranscriptSegment(0, 1000, "第一段")),
        "",
        "2026-06-12T08:05:00Z",
        "created",
        "updated");
    when(repository.listChannels()).thenReturn(List.of(channel));
    when(repository.listVideos("channel-row", 8)).thenReturn(List.of(video));

    List<YouTubeAdminService.DashboardChannel> dashboard = service.listDashboard();

    assertEquals(1, dashboard.size());
    assertEquals("", dashboard.get(0).videos().get(0).transcriptText());
    assertEquals(List.of(), dashboard.get(0).videos().get(0).transcriptSegments());
  }

  @Test
  void getCloudAudioLinkOnlySignsAliyunVideos() {
    var repository = mock(YouTubeRepository.class);
    var ossClient = mock(AliyunOssClient.class);
    var service = service(repository, mock(YouTubeClient.class), mock(YouTubeAudioDownloader.class), ossClient);
    when(repository.findVideo("vid-1")).thenReturn(Optional.of(video("vid-1", "aliyun_filetrans", "audio.wav", 0)));
    when(ossClient.signStoredAudio("audio.wav")).thenReturn("");
    when(ossClient.signVideoAudio("vid-1")).thenReturn("https://example.com/audio.wav");

    String signed = service.getCloudAudioLink("vid-1");

    assertEquals("https://example.com/audio.wav", signed);
  }

  @Test
  void ensureAudioAcceptsWindowsStyleSavedPath(@TempDir Path tempDir) throws Exception {
    Path readyAudio = tempDir.resolve("nested").resolve("voice.m4a");
    Files.createDirectories(readyAudio.getParent());
    Files.writeString(readyAudio, "x");
    var repository = mock(YouTubeRepository.class);
    var service = service(repository, mock(YouTubeClient.class), mock(YouTubeAudioDownloader.class));
    when(repository.findVideo("vid-1")).thenReturn(Optional.of(
        video("vid-1", "", readyAudio.toString().replace("/", "\\"), 1234)));

    VideoRecord saved = service.ensureAudio("vid-1");

    assertEquals(readyAudio.toString().replace("/", "\\"), saved.audioPath());
  }

  @Test
  void ensureAudioRedownloadsWhenSavedFileMissing(@TempDir Path tempDir) throws Exception {
    Path readyAudio = tempDir.resolve("fresh.m4a");
    Files.writeString(readyAudio, "x");
    var repository = mock(YouTubeRepository.class);
    var downloader = mock(YouTubeAudioDownloader.class);
    var service = service(repository, mock(YouTubeClient.class), downloader);
    when(repository.findVideo("vid-1")).thenReturn(Optional.of(
        video("vid-1", "", tempDir.resolve("missing.m4a").toString(), 0)));
    when(downloader.download("vid-1", "https://example.com/watch?v=1"))
        .thenReturn(new YouTubeAudioDownloader.AudioDownload(readyAudio.toString(), 1234));
    when(repository.saveVideo(any())).thenAnswer(call -> savedVideo(call.getArgument(0)));

    VideoRecord saved = service.ensureAudio("vid-1");
    ArgumentCaptor<SaveVideoCommand> captor = ArgumentCaptor.forClass(SaveVideoCommand.class);
    verify(repository).saveVideo(captor.capture());

    assertEquals(readyAudio.toString(), saved.audioPath());
    assertEquals(1234, saved.audioDurationMs());
    assertEquals(1234, captor.getValue().audioDurationMs());
  }

  @Test
  void getVideoReturnsNullWhenMissing() {
    var repository = mock(YouTubeRepository.class);
    var service = service(repository, mock(YouTubeClient.class), mock(YouTubeAudioDownloader.class));
    when(repository.findVideo("missing")).thenReturn(Optional.empty());

    assertNull(service.getVideo("missing"));
  }

  @Test
  void markVideoReadSetsUnreadVideo() {
    var repository = mock(YouTubeRepository.class);
    var service = service(repository, mock(YouTubeClient.class), mock(YouTubeAudioDownloader.class));
    VideoRecord unread = video("vid-1", "", "audio.m4a", 1234);
    when(repository.findVideo("vid-1")).thenReturn(Optional.of(unread));
    when(repository.markRead(eq("vid-1"), any(), any())).thenReturn(unread);

    service.markVideoRead("vid-1");

    verify(repository).markRead(eq("vid-1"), any(), any());
  }

  @Test
  void syncChannelReusesSavedTranscriptWithoutRetranscribing() {
    var repository = mock(YouTubeRepository.class);
    var client = mock(YouTubeClient.class);
    var downloader = mock(YouTubeAudioDownloader.class);
    var transcriber = mock(AliyunSpeechTranscriber.class);
    var notifier = notifier();
    var importer = importer();
    var service = service(repository, client, downloader, mock(AliyunOssClient.class), transcriber, notifier, importer, 1, 20);
    ChannelRecord channel = channel("2026-06-12T08:00:00Z");
    VideoRecord ready = new VideoRecord(
        "vid-1",
        "channel-row",
        "channel-id",
        "Video",
        "https://example.com/watch?v=1",
        "2026-06-12T08:00:00Z",
        "",
        1234,
        YouTubeAdminService.TRANSCRIPT_READY,
        "zh",
        "aliyun_filetrans",
        "已保存转写",
        List.of(new TranscriptSegment(0, 1000, "已保存转写")),
        "",
        "2026-06-12T08:05:00Z",
        "created",
        "updated");
    when(repository.findChannel("channel-row")).thenReturn(Optional.of(channel));
    when(client.listVideos("channel-id", 1)).thenReturn(List.of(videoMeta("vid-1", "2026-06-12T08:00:00Z")));
    when(repository.findVideo("vid-1")).thenReturn(Optional.of(ready));
    when(repository.saveChannel(any())).thenReturn(channel);

    YouTubeAdminService.SyncResult result = service.syncChannel("channel-row");

    assertEquals(1, result.videos().size());
    assertEquals("已保存转写", result.videos().get(0).transcriptText());
    verify(downloader, never()).download(any(), any());
    verify(transcriber, never()).transcribe(any());
    verify(importer).importIfReady(eq(channel), eq(ready));
    verify(notifier).notifyTranscriptReady(eq(channel), eq(ready));
  }

  @Test
  void syncChannelMarksQuotaFailureForMidnightRetry() {
    var repository = mock(YouTubeRepository.class);
    var client = mock(YouTubeClient.class);
    var downloader = mock(YouTubeAudioDownloader.class);
    var transcriber = mock(AliyunSpeechTranscriber.class);
    var notifier = notifier();
    var importer = importer();
    var service = service(repository, client, downloader, mock(AliyunOssClient.class), transcriber, notifier, importer, 1, 20);
    ChannelRecord channel = channel("2026-06-11T08:00:00Z");
    when(repository.findChannel("channel-row")).thenReturn(Optional.of(channel));
    when(client.listVideos("channel-id", 1)).thenReturn(List.of(videoMeta("vid-1", "2026-06-12T08:00:00Z")));
    when(repository.findVideo("vid-1")).thenReturn(Optional.empty());
    when(downloader.download("vid-1", "https://example.com/watch?v=1"))
        .thenReturn(new YouTubeAudioDownloader.AudioDownload("audio.m4a", 1234));
    when(transcriber.transcribe("audio.m4a"))
        .thenThrow(new AliyunFileTransClient.QuotaExceededException("阿里云配额不足"));
    when(repository.saveVideo(any())).thenAnswer(call -> savedVideo(call.getArgument(0)));
    when(repository.saveChannel(any())).thenReturn(channel);

    YouTubeAdminService.SyncResult result = service.syncChannel("channel-row");

    assertEquals(YouTubeAdminService.TRANSCRIPT_RETRY_MIDNIGHT, result.videos().get(0).transcriptStatus());
    assertTrue(result.videos().get(0).errorMessage().contains("0"));
    verify(importer, never()).importIfReady(any(), any());
    verify(notifier, never()).notifyTranscriptReady(any(), any());
  }

  @Test
  void retryQuotaLimitedVideosPushesWhenRetrySucceeds(@TempDir Path tempDir) throws Exception {
    Path readyAudio = tempDir.resolve("voice.m4a");
    Files.writeString(readyAudio, "x");
    var repository = mock(YouTubeRepository.class);
    var notifier = notifier();
    var transcriber = mock(AliyunSpeechTranscriber.class);
    var importer = importer();
    var service = service(
        repository,
        mock(YouTubeClient.class),
        mock(YouTubeAudioDownloader.class),
        mock(AliyunOssClient.class),
        transcriber,
        notifier,
        importer,
        1,
        5);
    ChannelRecord channel = channel("2026-06-12T08:00:00Z");
    VideoRecord queued = new VideoRecord(
        "vid-1",
        "channel-row",
        "channel-id",
        "Video",
        "https://example.com/watch?v=1",
        "2026-06-12T08:00:00Z",
        readyAudio.toString(),
        1234,
        YouTubeAdminService.TRANSCRIPT_RETRY_MIDNIGHT,
        "",
        "aliyun_filetrans",
        "",
        List.of(),
        "等待午夜重试",
        "2026-06-12T08:05:00Z",
        "created",
        "updated");
    when(repository.listByTranscriptStatus(YouTubeAdminService.TRANSCRIPT_RETRY_MIDNIGHT, 5))
        .thenReturn(List.of(queued));
    when(repository.findChannel("channel-row")).thenReturn(Optional.of(channel));
    when(repository.findVideo("vid-1")).thenReturn(Optional.of(queued));
    when(transcriber.transcribe(readyAudio.toString())).thenReturn(new AliyunSpeechTranscriber.TranscriptResult(
        "ready",
        "zh",
        "aliyun_filetrans",
        "识别完成",
        List.of(new TranscriptSegment(0, 1000, "识别完成")),
        ""));
    when(repository.saveVideo(any())).thenAnswer(call -> savedVideo(call.getArgument(0)));

    int retried = service.retryQuotaLimitedVideos();

    assertEquals(1, retried);
    verify(importer).importIfReady(eq(channel), any());
    verify(notifier).notifyTranscriptReady(eq(channel), any());
  }

  @Test
  void syncUpdatedChannelsSkipsUnchangedLatestVideo() {
    var repository = mock(YouTubeRepository.class);
    var client = mock(YouTubeClient.class);
    var downloader = mock(YouTubeAudioDownloader.class);
    var transcriber = mock(AliyunSpeechTranscriber.class);
    var notifier = notifier();
    var importer = importer();
    var service = service(repository, client, downloader, mock(AliyunOssClient.class), transcriber, notifier, importer, 1, 20);
    ChannelRecord channel = channel("2026-06-12T08:00:00Z");
    when(repository.listChannels()).thenReturn(List.of(channel));
    when(client.listVideos("channel-id", 1)).thenReturn(List.of(videoMeta("vid-1", "2026-06-12T08:00:00Z")));
    when(repository.saveChannel(any())).thenReturn(channel);

    YouTubeAdminService.AutoSyncSummary summary = service.syncUpdatedChannels();

    assertEquals(1, summary.checkedChannels());
    assertEquals(0, summary.updatedChannels());
    assertEquals(0, summary.processedVideos());
    verify(downloader, never()).download(any(), any());
    verify(transcriber, never()).transcribe(any());
    verify(importer, never()).importIfReady(any(), any());
    verify(notifier, never()).notifyTranscriptReady(any(), any());
  }

  @Test
  void syncUpdatedChannelsProcessesNewLatestVideo() {
    var repository = mock(YouTubeRepository.class);
    var client = mock(YouTubeClient.class);
    var downloader = mock(YouTubeAudioDownloader.class);
    var transcriber = mock(AliyunSpeechTranscriber.class);
    var notifier = notifier();
    var importer = importer();
    var service = service(repository, client, downloader, mock(AliyunOssClient.class), transcriber, notifier, importer, 1, 20);
    ChannelRecord channel = channel("2026-06-11T08:00:00Z");
    when(repository.listChannels()).thenReturn(List.of(channel));
    when(client.listVideos("channel-id", 1)).thenReturn(List.of(videoMeta("vid-2", "2026-06-12T08:00:00Z")));
    when(repository.findVideo("vid-2")).thenReturn(Optional.empty());
    when(downloader.download("vid-2", "https://example.com/watch?v=2"))
        .thenReturn(new YouTubeAudioDownloader.AudioDownload("audio-2.m4a", 1234));
    when(transcriber.transcribe("audio-2.m4a")).thenReturn(new AliyunSpeechTranscriber.TranscriptResult(
        "ready",
        "zh",
        "aliyun_filetrans",
        "识别完成",
        List.of(new TranscriptSegment(0, 1000, "识别完成")),
        ""));
    when(repository.saveVideo(any())).thenAnswer(call -> savedVideo(call.getArgument(0)));
    when(repository.saveChannel(any())).thenReturn(channel("2026-06-12T08:00:00Z"));

    YouTubeAdminService.AutoSyncSummary summary = service.syncUpdatedChannels();

    assertEquals(1, summary.checkedChannels());
    assertEquals(1, summary.updatedChannels());
    assertEquals(1, summary.processedVideos());
    verify(importer).importIfReady(any(), any());
    verify(notifier).notifyTranscriptReady(any(), any());
  }

  private YouTubeAdminService service(
      YouTubeRepository repository,
      YouTubeClient client,
      YouTubeAudioDownloader downloader) {
    return service(
        repository,
        client,
        downloader,
        mock(AliyunOssClient.class),
        mock(AliyunSpeechTranscriber.class),
        notifier(),
        importer(),
        1,
        20);
  }

  private YouTubeAdminService service(
      YouTubeRepository repository,
      YouTubeClient client,
      YouTubeAudioDownloader downloader,
      AliyunOssClient ossClient) {
    return service(
        repository,
        client,
        downloader,
        ossClient,
        mock(AliyunSpeechTranscriber.class),
        notifier(),
        importer(),
        1,
        20);
  }

  private YouTubeAdminService service(
      YouTubeRepository repository,
      YouTubeClient client,
      YouTubeAudioDownloader downloader,
      AliyunOssClient ossClient,
      AliyunSpeechTranscriber transcriber,
      YouTubeTranscriptNotifier notifier,
      YouTubeOpinionAutoImportService importer,
      int maxVideos,
      int retryBatchLimit) {
    return new YouTubeAdminService(
        repository,
        client,
        downloader,
        ossClient,
        transcriber,
        notifier,
        importer,
        maxVideos,
        retryBatchLimit);
  }

  private ChannelRecord channel(String lastVideoPublishedAt) {
    return new ChannelRecord(
        "channel-row",
        "channel-id",
        "Channel",
        "@channel",
        "https://example.com/channel",
        true,
        "",
        lastVideoPublishedAt,
        "created",
        "updated");
  }

  private YouTubeClient.VideoMetadata videoMeta(String videoId, String publishedAt) {
    return new YouTubeClient.VideoMetadata(
        videoId,
        videoId.equals("vid-2") ? "Video 2" : "Video",
        "https://example.com/watch?v=" + videoId.substring(4),
        publishedAt);
  }

  private VideoRecord video(String videoId, String transcriptSource, String audioPath, long durationMs) {
    return new VideoRecord(
        videoId,
        "channel-row",
        "channel-id",
        "Video",
        "https://example.com/watch?v=1",
        "2026-06-12T08:00:00Z",
        audioPath,
        durationMs,
        YouTubeAdminService.TRANSCRIPT_READY,
        "zh",
        transcriptSource,
        "",
        List.of(),
        "",
        "2026-06-12T08:05:00Z",
        "created",
        "updated");
  }

  private VideoRecord savedVideo(SaveVideoCommand command) {
    return new VideoRecord(
        command.videoId(),
        command.channelRowId(),
        command.channelId(),
        command.title(),
        command.videoUrl(),
        command.publishedAt(),
        command.audioPath(),
        command.audioDurationMs(),
        command.transcriptStatus(),
        command.transcriptLanguage(),
        command.transcriptSource(),
        command.transcriptText(),
        command.transcriptSegments(),
        command.errorMessage(),
        command.syncedAt(),
        "created",
        command.updatedAt());
  }

  private YouTubeTranscriptNotifier notifier() {
    var notifier = mock(YouTubeTranscriptNotifier.class);
    when(notifier.notifyTranscriptReady(any(), any()))
        .thenReturn(new WxPusherPushClient.PushResult(true, "SENT", ""));
    return notifier;
  }

  private YouTubeOpinionAutoImportService importer() {
    return mock(YouTubeOpinionAutoImportService.class);
  }
}
