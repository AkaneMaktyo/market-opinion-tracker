package com.personal.tracker.service.youtube.schedule;

import com.personal.tracker.service.youtube.YouTubeAdminService;
import org.springframework.core.env.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class YouTubeChannelSyncScheduler {
  private static final Logger log = LoggerFactory.getLogger(YouTubeChannelSyncScheduler.class);
  private final YouTubeAdminService service;
  private final boolean enabled;

  public YouTubeChannelSyncScheduler(YouTubeAdminService service, Environment environment) {
    this.service = service;
    this.enabled = !"false".equalsIgnoreCase(
        environment.getProperty("YOUTUBE_CHANNEL_SYNC_ENABLED", "true"));
  }

  @Scheduled(
      cron = "${YOUTUBE_CHANNEL_SYNC_CRON:0 */5 * * * *}",
      zone = "${YOUTUBE_CHANNEL_SYNC_ZONE:Asia/Shanghai}")
  public void syncUpdatedChannels() {
    if (!enabled) {
      return;
    }
    try {
      YouTubeAdminService.AutoSyncSummary summary = service.syncUpdatedChannels();
      if (summary.updatedChannels() > 0) {
        log.info(
            "YouTube direct sync imported videos: checked={}, updated={}, videos={}",
            summary.checkedChannels(),
            summary.updatedChannels(),
            summary.processedVideos());
      }
    } catch (Exception error) {
      log.warn("YouTube direct sync skipped: {}", error.getMessage());
    }
  }
}
