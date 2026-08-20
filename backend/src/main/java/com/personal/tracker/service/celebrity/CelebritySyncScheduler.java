package com.personal.tracker.service.celebrity;

import com.personal.tracker.config.celebrity.CelebrityDataProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CelebritySyncScheduler {
  private final CelebrityPortfolioService service;
  private final CelebrityDataProperties properties;

  public CelebritySyncScheduler(CelebrityPortfolioService service, CelebrityDataProperties properties) {
    this.service = service;
    this.properties = properties;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onStartup() {
    if (properties.enabled() && properties.startupSyncEnabled()) {
      service.syncAsync("启动");
    }
  }

  @Scheduled(cron = "${CELEBRITY_DATA_SYNC_CRON:0 10 */6 * * *}", zone = "Asia/Shanghai")
  public void scheduledSync() {
    if (properties.enabled()) {
      service.syncAsync("定时");
    }
  }
}
