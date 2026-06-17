package com.personal.tracker.service.youtube;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class YouTubeRetryScheduler {
  private static final Logger log = LoggerFactory.getLogger(YouTubeRetryScheduler.class);
  private final YouTubeAdminService service;

  public YouTubeRetryScheduler(YouTubeAdminService service) {
    this.service = service;
  }

  @Scheduled(
      cron = "${YOUTUBE_TRANSCRIPT_RETRY_CRON:0 0 0 * * *}",
      zone = "${YOUTUBE_TRANSCRIPT_RETRY_ZONE:Asia/Shanghai}")
  public void retryQuotaLimitedVideos() {
    int processed = service.retryQuotaLimitedVideos();
    if (processed > 0) {
      log.info("YouTube 午夜重试已发起: {}", processed);
    }
  }
}
