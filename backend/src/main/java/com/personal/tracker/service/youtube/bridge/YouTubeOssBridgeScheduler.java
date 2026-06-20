package com.personal.tracker.service.youtube.bridge;

import com.personal.tracker.service.youtube.bridge.YouTubeOssBridgeService.BridgeSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class YouTubeOssBridgeScheduler {
  private static final Logger log = LoggerFactory.getLogger(YouTubeOssBridgeScheduler.class);
  private final YouTubeOssBridgeService service;

  public YouTubeOssBridgeScheduler(YouTubeOssBridgeService service) {
    this.service = service;
  }

  @Scheduled(
      cron = "${YOUTUBE_OSS_BRIDGE_CRON:30 */5 * * * *}",
      zone = "${YOUTUBE_OSS_BRIDGE_ZONE:Asia/Shanghai}")
  public void syncBridge() {
    try {
      BridgeSummary summary = service.sync();
      if (summary.importedVideos() > 0) {
        log.info(
            "YouTube OSS 桥接导入完成: exportedChannels={}, importedVideos={}",
            summary.exportedChannels(),
            summary.importedVideos());
      }
    } catch (Exception error) {
      log.warn("YouTube OSS 桥接同步失败: {}", error.getMessage());
    }
  }
}
