package com.personal.tracker.service.youtube.schedule;

import com.personal.tracker.service.youtube.notify.YouTubeNotificationRetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class YouTubeNotificationRetryScheduler {
  private static final Logger log = LoggerFactory.getLogger(YouTubeNotificationRetryScheduler.class);
  private final YouTubeNotificationRetryService service;

  public YouTubeNotificationRetryScheduler(YouTubeNotificationRetryService service) {
    this.service = service;
  }

  @Scheduled(
      cron = "${YOUTUBE_NOTIFY_RETRY_CRON:15 */2 * * * *}",
      zone = "${YOUTUBE_NOTIFY_RETRY_ZONE:Asia/Shanghai}")
  public void retryPendingNotifications() {
    try {
      int retried = service.retryPendingNotifications();
      if (retried > 0) {
        log.info("YouTube 通知补发完成: {}", retried);
      }
    } catch (RuntimeException error) {
      log.warn("YouTube 通知补发失败: {}", error.getMessage());
    }
  }
}
