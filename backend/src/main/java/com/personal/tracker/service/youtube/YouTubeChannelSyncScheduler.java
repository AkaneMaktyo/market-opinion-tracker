package com.personal.tracker.service.youtube;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class YouTubeChannelSyncScheduler {
  private static final Logger log = LoggerFactory.getLogger(YouTubeChannelSyncScheduler.class);
  private final YouTubeAdminService service;

  public YouTubeChannelSyncScheduler(YouTubeAdminService service) {
    this.service = service;
  }

  @Scheduled(
      cron = "${YOUTUBE_CHANNEL_SYNC_CRON:0 */5 * * * *}",
      zone = "${YOUTUBE_CHANNEL_SYNC_ZONE:Asia/Shanghai}")
  public void syncUpdatedChannels() {
    YouTubeAdminService.AutoSyncSummary summary = service.syncUpdatedChannels();
    if (summary.updatedChannels() > 0) {
      log.info(
          "YouTube 自动同步发现新视频: checked={}, updated={}, videos={}",
          summary.checkedChannels(),
          summary.updatedChannels(),
          summary.processedVideos());
    }
  }
}
