package com.personal.tracker.service.positions;

import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.WxPusherBlogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KolPositionRebuildScheduler {
  private static final Logger log = LoggerFactory.getLogger(KolPositionRebuildScheduler.class);
  private final KolPositionRebuildService rebuildService;
  private final WxPusherBloggerRepository bloggers;
  private final boolean enabled;

  public KolPositionRebuildScheduler(
      KolPositionRebuildService rebuildService,
      WxPusherBloggerRepository bloggers,
      @Value("${app.positions.auto-rebuild-enabled:true}") boolean enabled) {
    this.rebuildService = rebuildService;
    this.bloggers = bloggers;
    this.enabled = enabled;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onStartup() {
    if (enabled) {
      runAll("启动");
    }
  }

  @Scheduled(cron = "0 20 * * * *", zone = "Asia/Shanghai")
  public void onSchedule() {
    if (enabled) {
      runAll("定时");
    }
  }

  private void runAll(String trigger) {
    for (WxPusherBlogger blogger : bloggers.enabled()) {
      try {
        var result = rebuildService.rebuild(blogger.kolId(), null);
        log.info("[{}]虚拟跟单自动结算 kol={} trades={} settled={} running={}",
            trigger, result.kolId(), result.totalTrades(),
            result.settledTrades(), result.runningTrades());
      } catch (RuntimeException error) {
        log.warn("[{}]虚拟跟单自动结算失败 kol={}", trigger, blogger.kolId(), error);
      }
    }
  }
}
