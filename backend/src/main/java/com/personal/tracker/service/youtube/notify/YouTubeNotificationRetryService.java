package com.personal.tracker.service.youtube.notify;

import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.repository.youtube.YouTubeRepository;
import com.personal.tracker.repository.youtube.YouTubeRepository.ChannelRecord;
import com.personal.tracker.repository.youtube.YouTubeRepository.VideoRecord;
import com.personal.tracker.service.youtube.YouTubeTranscriptNotifier;
import com.personal.tracker.service.youtube.YouTubeVideoSupport;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class YouTubeNotificationRetryService {
  private final YouTubeRepository repository;
  private final YouTubeTranscriptNotifier notifier;
  private final int batchLimit;

  public YouTubeNotificationRetryService(
      YouTubeRepository repository,
      YouTubeTranscriptNotifier notifier,
      Environment environment) {
    this.repository = repository;
    this.notifier = notifier;
    this.batchLimit = batchLimit(environment);
  }

  public int retryPendingNotifications() {
    if (!notifier.pushReady()) {
      return 0;
    }
    int retried = 0;
    for (VideoRecord video : repository.listVideosPendingNotification(batchLimit)) {
      if (!YouTubeVideoSupport.hasReadyTranscript(video) || alreadyNotified(video)) {
        continue;
      }
      ChannelRecord channel = repository.findChannel(video.channelRowId())
          .orElse(YouTubeVideoSupport.baseChannel(video));
      var result = notifier.notifyTranscriptReady(channel, video);
      String notifiedAt = result.ok() ? JdbcSupport.now() : "";
      repository.markNotification(
          video.videoId(),
          result.status(),
          result.error(),
          notifiedAt,
          JdbcSupport.now());
      retried++;
    }
    return retried;
  }

  private boolean alreadyNotified(VideoRecord video) {
    return video != null
        && "SENT".equalsIgnoreCase(video.notifyStatus())
        && video.notifiedAt() != null
        && !video.notifiedAt().isBlank();
  }

  private static int batchLimit(Environment environment) {
    String raw = environment.getProperty("YOUTUBE_NOTIFY_RETRY_BATCH_LIMIT");
    if (raw == null || raw.isBlank()) {
      return 20;
    }
    try {
      return Math.max(1, Integer.parseInt(raw.trim()));
    } catch (NumberFormatException error) {
      return 20;
    }
  }
}
