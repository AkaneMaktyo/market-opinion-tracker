package com.personal.tracker.service;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.service.MarketDataService.RefreshResult;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MarketDataStartupSyncService {
  private static final Logger log = LoggerFactory.getLogger(MarketDataStartupSyncService.class);
  private static final List<String> TIMEFRAMES = List.of("1D", "1H", "4H");
  private static final int WORKERS = 4;

  private final MarketDataService marketData;
  private final boolean enabled;

  public MarketDataStartupSyncService(
      MarketDataService marketData,
      @Value("${market-data.startup-sync-enabled:false}") boolean enabled) {
    this.marketData = marketData;
    this.enabled = enabled;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void start() {
    if (!enabled) {
      log.info("启动 K 线后台更新已关闭。");
      return;
    }
    Thread worker = new Thread(this::syncAll, "market-bar-startup-sync");
    worker.setDaemon(true);
    worker.start();
  }

  private void syncAll() {
    List<Instrument> instruments = marketData.instruments();
    AtomicInteger success = new AtomicInteger();
    AtomicInteger skipped = new AtomicInteger();
    AtomicInteger failed = new AtomicInteger();
    log.info("启动 K 线后台更新：{} 个品种，周期 {}，并发 {}", instruments.size(), TIMEFRAMES, WORKERS);
    ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
    for (Instrument instrument : instruments) {
      executor.submit(() -> syncInstrument(instrument, success, skipped, failed));
    }
    executor.shutdown();
    try {
      executor.awaitTermination(30, TimeUnit.MINUTES);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      log.warn("启动 K 线后台更新被中断", error);
    }
    log.info("启动 K 线后台更新结束：成功 {}，跳过 {}，失败 {}",
        success.get(), skipped.get(), failed.get());
  }

  private void syncInstrument(
      Instrument instrument,
      AtomicInteger success,
      AtomicInteger skipped,
      AtomicInteger failed) {
    for (String timeframe : TIMEFRAMES) {
      try {
        RefreshResult result = marketData.refreshBars(instrument, timeframe);
        if (result.skipped()) {
          skipped.incrementAndGet();
        } else {
          success.incrementAndGet();
        }
        log.info("K 线更新完成：{} {}，获取 {} 根",
            result.symbol(), timeframe, result.fetched());
      } catch (RuntimeException error) {
        failed.incrementAndGet();
        log.warn("K 线更新失败：{} {}", instrument.symbol(), timeframe, error);
      }
    }
  }
}
