package com.personal.tracker.service;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.service.MarketDataService.BackfillResult;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MarketDataBackfillService {
  private static final Logger log = LoggerFactory.getLogger(MarketDataBackfillService.class);
  private static final List<String> TIMEFRAMES = List.of("1D", "1H", "4H");
  private static final int WORKERS = 8;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private final MarketDataService marketData;
  private volatile BackfillStatus status = BackfillStatus.idle();

  public MarketDataBackfillService(MarketDataService marketData) {
    this.marketData = marketData;
  }

  public BackfillStatus startAll() {
    if (!running.compareAndSet(false, true)) {
      return status.withMessage("深度回填正在运行");
    }
    status = BackfillStatus.running(0, "ALL", null, "全部品种深度回填已启动");
    Thread worker = new Thread(() -> run(marketData.instruments(), "ALL", null),
        "market-bar-deep-backfill");
    worker.setDaemon(true);
    worker.start();
    return status;
  }

  public BackfillStatus startSymbol(String symbol) {
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("请先选择要回填的品种");
    }
    if (!running.compareAndSet(false, true)) {
      return status.withMessage("深度回填正在运行");
    }
    Instrument item = marketData.instrument(symbol);
    status = BackfillStatus.running(0, "SYMBOL", item.symbol(), "当前品种深度回填已启动");
    Thread worker = new Thread(() -> run(List.of(item), "SYMBOL", item.symbol()),
        "market-bar-symbol-backfill");
    worker.setDaemon(true);
    worker.start();
    return status;
  }

  public BackfillStatus status() {
    return status;
  }

  private void run(List<Instrument> items, String scope, String symbol) {
    AtomicInteger processed = new AtomicInteger();
    AtomicInteger success = new AtomicInteger();
    AtomicInteger skipped = new AtomicInteger();
    AtomicInteger failed = new AtomicInteger();
    AtomicInteger fetchedBars = new AtomicInteger();
    int total = items.size() * TIMEFRAMES.size();
    status = BackfillStatus.running(total, scope, symbol, "深度回填运行中");
    log.info("手动 K 线深度回填开始：{} 个品种，周期 {}，并发 {}",
        items.size(), TIMEFRAMES, WORKERS);
    ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
    try {
      for (Instrument item : items) {
        for (String timeframe : TIMEFRAMES) {
          executor.submit(() -> backfillTimeframe(
              item, timeframe, total, processed, success, skipped, failed, fetchedBars));
        }
      }
      executor.shutdown();
      boolean completed = await(executor);
      status = completed ? status.finish("DONE", "深度回填完成")
          : status.finish("FAILED", "深度回填超时或被中断");
    } finally {
      if (!executor.isTerminated()) {
        executor.shutdownNow();
      }
      running.set(false);
    }
    log.info("手动 K 线深度回填结束：成功 {}，跳过 {}，失败 {}，写入/覆盖 {} 根",
        success.get(), skipped.get(), failed.get(), fetchedBars.get());
  }

  private void backfillTimeframe(
      Instrument item,
      String timeframe,
      int total,
      AtomicInteger processed,
      AtomicInteger success,
      AtomicInteger skipped,
      AtomicInteger failed,
      AtomicInteger fetchedBars) {
    try {
      BackfillResult result = marketData.deepBackfillBars(item, timeframe);
      fetchedBars.addAndGet(result.fetched());
      if (result.skipped()) {
        skipped.incrementAndGet();
      } else {
        success.incrementAndGet();
      }
      log.info("深度回填完成：{} {}，{} 页，{} 根",
          result.symbol(), timeframe, result.pages(), result.fetched());
    } catch (RuntimeException error) {
      failed.incrementAndGet();
      log.warn("深度回填失败：{} {}", item.symbol(), timeframe, error);
    } finally {
      update(total, processed.incrementAndGet(), success, skipped, failed, fetchedBars);
    }
  }

  private void update(
      int total,
      int processed,
      AtomicInteger success,
      AtomicInteger skipped,
      AtomicInteger failed,
      AtomicInteger fetchedBars) {
    status = status.progress(
        total,
        processed,
        success.get(),
        skipped.get(),
        failed.get(),
        fetchedBars.get());
  }

  private boolean await(ExecutorService executor) {
    try {
      return executor.awaitTermination(6, TimeUnit.HOURS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      log.warn("手动 K 线深度回填被中断", error);
      return false;
    }
  }

  public record BackfillStatus(
      String state,
      int total,
      int processed,
      int success,
      int skipped,
      int failed,
      int fetchedBars,
      String message,
      String scope,
      String symbol,
      String startedAt,
      String finishedAt) {
    static BackfillStatus idle() {
      return new BackfillStatus("IDLE", 0, 0, 0, 0, 0, 0, "尚未运行", "ALL", null, null, null);
    }

    static BackfillStatus running(int total, String scope, String symbol, String message) {
      return new BackfillStatus(
          "RUNNING", total, 0, 0, 0, 0, 0, message, scope, symbol, JdbcSupport.now(), null);
    }

    BackfillStatus progress(
        int nextTotal,
        int nextProcessed,
        int nextSuccess,
        int nextSkipped,
        int nextFailed,
        int nextFetchedBars) {
      return new BackfillStatus(
          state,
          nextTotal,
          nextProcessed,
          nextSuccess,
          nextSkipped,
          nextFailed,
          nextFetchedBars,
          message,
          scope,
          symbol,
          startedAt,
          finishedAt);
    }

    BackfillStatus finish(String nextState, String nextMessage) {
      return new BackfillStatus(
          nextState,
          total,
          processed,
          success,
          skipped,
          failed,
          fetchedBars,
          nextMessage,
          scope,
          symbol,
          startedAt,
          JdbcSupport.now());
    }

    BackfillStatus withMessage(String nextMessage) {
      return new BackfillStatus(
          state,
          total,
          processed,
          success,
          skipped,
          failed,
          fetchedBars,
          nextMessage,
          scope,
          symbol,
          startedAt,
          finishedAt);
    }
  }
}
