package com.personal.tracker.service.youtube;

import com.personal.tracker.repository.youtube.YouTubeRepository.ChannelRecord;
import com.personal.tracker.repository.youtube.YouTubeRepository.TranscriptSegment;
import com.personal.tracker.repository.youtube.YouTubeRepository.VideoRecord;
import java.util.ArrayList;
import java.util.List;

public final class YouTubeVideoSupport {
  private YouTubeVideoSupport() {
  }

  public static boolean hasReadyTranscript(VideoRecord video) {
    if (video == null || !YouTubeAdminService.TRANSCRIPT_READY.equals(video.transcriptStatus())) {
      return false;
    }
    if (video.transcriptSegments() != null && !video.transcriptSegments().isEmpty()) {
      return true;
    }
    return video.transcriptText() != null && !video.transcriptText().isBlank();
  }

  public static String nextLatest(String currentLatest, List<YouTubeClient.VideoMetadata> candidates) {
    String nextLatest = currentLatest == null ? "" : currentLatest;
    for (YouTubeClient.VideoMetadata video : safe(candidates)) {
      String publishedAt = video.publishedAt() == null ? "" : video.publishedAt();
      if (publishedAt.compareTo(nextLatest) > 0) {
        nextLatest = publishedAt;
      }
    }
    return nextLatest;
  }

  public static VideoRecord baseVideo(
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
        "",
        "",
        "",
        "");
  }

  public static ChannelRecord baseChannel(VideoRecord video) {
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

  public static VideoRecord summaryVideo(VideoRecord video) {
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
        video.notifyStatus(),
        video.notifyError(),
        video.notifiedAt(),
        video.syncedAt(),
        video.createdAt(),
        video.updatedAt());
  }

  public static String transcriptText(VideoRecord video) {
    if (video == null) {
      return "";
    }
    if (video.transcriptSegments() != null && !video.transcriptSegments().isEmpty()) {
      List<String> lines = new ArrayList<>();
      for (TranscriptSegment segment : video.transcriptSegments()) {
        if (segment != null && segment.text() != null && !segment.text().isBlank()) {
          lines.add(segment.text().trim());
        }
      }
      String joined = String.join("\n", lines).trim();
      if (!joined.isBlank()) {
        return joined;
      }
    }
    return video.transcriptText() == null ? "" : video.transcriptText().trim();
  }

  private static List<YouTubeClient.VideoMetadata> safe(List<YouTubeClient.VideoMetadata> videos) {
    return videos == null ? List.of() : videos;
  }
}
