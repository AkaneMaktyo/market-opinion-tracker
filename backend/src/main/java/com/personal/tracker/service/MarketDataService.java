package com.personal.tracker.service;

import com.personal.tracker.config.MarketDataProperties;
import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.MarketBar;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.MarketBarRepository;
import com.personal.tracker.repository.MarketBarRepository.BarCoverage;
import com.personal.tracker.service.market.MarketBarProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MarketDataService {
  private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);
  private static final int LIMIT = 1000;
  private static final int DEFAULT_VISIBLE_LIMIT = 600;
  private static final int LATEST_LIMIT = 3;
  private static final int DEEP_BACKFILL_MAX_PAGES = 80;
  private static final long EMPTY_REFRESH_TTL_MS = TimeUnit.SECONDS.toMillis(45);
  private static final long SUMMARY_REFRESH_TTL_MS = TimeUnit.SECONDS.toMillis(15);
  private static final long WARM_REFRESH_TTL_MS = TimeUnit.MINUTES.toMillis(10);

  private final InstrumentRepository instruments;
  private final MarketBarRepository bars;
  private final List<MarketBarProvider> providers;
  private final Map<String, MarketBarProvider> providersByName;
  private final ExecutorService interactiveRefresh = Executors.newFixedThreadPool(2, task -> {
    Thread thread = new Thread(task, "market-bar-interactive-refresh");
    thread.setDaemon(true);
    return thread;
  });
  private final ExecutorService summaryRefresh = Executors.newFixedThreadPool(1, task -> {
    Thread thread = new Thread(task, "market-bar-summary-refresh");
    thread.setDaemon(true);
    return thread;
  });
  private final Set<String> refreshInFlight = ConcurrentHashMap.newKeySet();
  private final Map<String, Long> lastInteractiveRefresh = new ConcurrentHashMap<>();

  public MarketDataService(
      InstrumentRepository instruments,
      MarketBarRepository bars,
      List<MarketBarProvider> providers,
      MarketDataProperties properties) {
    this.instruments = instruments;
    this.bars = bars;
    this.providersByName = providerMap(providers);
    this.providers = orderedProviders(providers, properties.providers());
    log.info("K 线供应商顺序：{}", this.providers.stream().map(MarketBarProvider::name).toList());
  }

  public List<MarketBar> barsForSymbol(String symbol, String timeframe) {
    return barsForSymbol(symbol, timeframe, DEFAULT_VISIBLE_LIMIT, null);
  }

  public List<MarketBar> barsForSymbol(
      String symbol,
      String timeframe,
      int limit,
      String before) {
    Instrument instrument = instruments.saveIfAbsent(symbol, symbol, "US", null);
    String frame = timeframe == null || timeframe.isBlank() ? "1D" : timeframe;
    List<MarketBar> stored = bars.findRecentBars(instrument.id(), frame, limit, before);
    queueInteractiveRefresh(instrument, frame, stored.isEmpty());
    return stored;
  }

  public Optional<MarketBar> fetchLatestBar(Instrument instrument, String timeframe) {
    String frame = timeframe == null || timeframe.isBlank() ? "1D" : timeframe;
    long end = Instant.now().toEpochMilli();
    long start = end - latestLookbackMillis(frame);
    return fetch(instrument, frame, start, end, LATEST_LIMIT).stream()
        .max(java.util.Comparator.comparing(MarketBar::barTime));
  }

  public List<Instrument> instruments() {
    return instruments.findAll(null);
  }

  public Instrument instrument(String symbol) {
    return instruments.saveIfAbsent(symbol, symbol, "US", null);
  }

  public RefreshResult refreshBars(Instrument instrument, String timeframe) {
    String frame = timeframe == null || timeframe.isBlank() ? "1D" : timeframe;
    List<MarketBar> fetched = fetch(instrument, frame, null, null, LIMIT);
    if (fetched.isEmpty()) {
      return new RefreshResult(instrument.symbol(), frame, 0, true);
    }
    bars.saveAll(fetched);
    pause();
    return new RefreshResult(instrument.symbol(), frame, fetched.size(), false);
  }

  public BackfillResult deepBackfillBars(Instrument instrument, String timeframe) {
    String frame = timeframe == null || timeframe.isBlank() ? "1D" : timeframe;
    long cursor = initialBackfillCursor(instrument.id(), frame);
    long oldestSeen = Long.MAX_VALUE;
    int fetchedTotal = 0;
    int pages = 0;
    for (int page = 0; page < DEEP_BACKFILL_MAX_PAGES; page++) {
      List<MarketBar> fetched = fetch(instrument, frame, null, cursor, LIMIT);
      pause();
      if (fetched.isEmpty()) {
        break;
      }
      bars.saveAll(fetched);
      long pageOldest = oldestMillis(frame, fetched);
      if (pageOldest >= oldestSeen) {
        break;
      }
      fetchedTotal += fetched.size();
      pages++;
      oldestSeen = pageOldest;
      cursor = pageOldest - 1;
    }
    return new BackfillResult(instrument.symbol(), frame, fetchedTotal, pages, fetchedTotal == 0);
  }

  public void queueSummaryRefresh(Instrument instrument, boolean emptyStoredData) {
    queueSummaryRefresh(instrument, emptyStoredData, false);
  }

  public void queueSummaryRefresh(
      Instrument instrument,
      boolean emptyStoredData,
      boolean priority) {
    long ttl = emptyStoredData ? EMPTY_REFRESH_TTL_MS : SUMMARY_REFRESH_TTL_MS;
    queueRefresh(instrument, "1D", ttl, priority);
  }

  private long initialBackfillCursor(String instrumentId, String timeframe) {
    BarCoverage coverage = bars.coverage(instrumentId, timeframe);
    if (coverage != null && coverage.count() > 0 && present(coverage.firstBarTime())) {
      return barTimeMillis(timeframe, coverage.firstBarTime()) - 1;
    }
    return Instant.now().toEpochMilli();
  }

  private List<MarketBar> fetch(
      Instrument instrument,
      String timeframe,
      Long start,
      Long end,
      int limit) {
    for (MarketBarProvider provider : providersFor(instrument)) {
      List<MarketBar> fetched = provider.fetch(instrument, timeframe, start, end, limit);
      if (!fetched.isEmpty()) {
        return fetched;
      }
    }
    return List.of();
  }

  private void queueInteractiveRefresh(Instrument instrument, String timeframe, boolean emptyStoredData) {
    long ttl = emptyStoredData ? EMPTY_REFRESH_TTL_MS : WARM_REFRESH_TTL_MS;
    queueRefresh(instrument, timeframe, ttl, false);
  }

  private void queueRefresh(
      Instrument instrument,
      String timeframe,
      long ttl,
      boolean priority) {
    String key = instrument.id() + ":" + timeframe;
    long now = System.currentTimeMillis();
    Long last = lastInteractiveRefresh.get(key);
    if (last != null && now - last < ttl) {
      return;
    }
    if (!refreshInFlight.add(key)) {
      return;
    }
    ExecutorService executor = priority ? summaryRefresh : interactiveRefresh;
    executor.submit(() -> refreshInteractive(instrument, timeframe, key));
  }

  private void refreshInteractive(Instrument instrument, String timeframe, String key) {
    try {
      RefreshResult result = refreshBars(instrument, timeframe);
      log.info("交互 K 线后台刷新完成：{} {}，获取 {} 根",
          result.symbol(), result.timeframe(), result.fetched());
    } catch (RuntimeException error) {
      log.warn("交互 K 线后台刷新失败：{} {}", instrument.symbol(), timeframe, error);
    } finally {
      lastInteractiveRefresh.put(key, System.currentTimeMillis());
      refreshInFlight.remove(key);
    }
  }

  @PreDestroy
  public void shutdown() {
    interactiveRefresh.shutdownNow();
    summaryRefresh.shutdownNow();
  }

  private List<MarketBarProvider> providersFor(Instrument instrument) {
    String preferred = normalizeProvider(instrument.marketDataProvider());
    if (preferred == null) {
      return providers;
    }
    MarketBarProvider provider = providersByName.get(preferred);
    if (provider == null) {
      log.warn("Instrument {} configured unknown market provider: {}", instrument.symbol(), preferred);
      return providers;
    }
    List<MarketBarProvider> selected = new ArrayList<>();
    selected.add(provider);
    appendMissing(selected, providers);
    return selected;
  }

  private static String normalizeProvider(String value) {
    if (value == null || value.isBlank() || "auto".equalsIgnoreCase(value)) {
      return null;
    }
    return value.trim().toLowerCase();
  }

  private static List<MarketBarProvider> orderedProviders(
      List<MarketBarProvider> providers,
      List<String> configured) {
    if (providers == null || providers.isEmpty()) {
      return List.of();
    }
    Map<String, MarketBarProvider> byName = providerMap(providers);
    List<MarketBarProvider> selected = new ArrayList<>();
    for (String name : configured) {
      if ("all".equals(name)) {
        appendMissing(selected, providers);
        continue;
      }
      MarketBarProvider provider = byName.get(name);
      if (provider == null) {
        log.warn("未知 K 线供应商配置：{}", name);
        continue;
      }
      selected.add(provider);
    }
    return selected.isEmpty() ? providers : dedupe(selected);
  }

  private static void appendMissing(
      List<MarketBarProvider> selected,
      List<MarketBarProvider> providers) {
    for (MarketBarProvider provider : providers) {
      if (!selected.contains(provider)) {
        selected.add(provider);
      }
    }
  }

  private static List<MarketBarProvider> dedupe(List<MarketBarProvider> providers) {
    return new ArrayList<>(new LinkedHashSet<>(providers));
  }

  private static Map<String, MarketBarProvider> providerMap(List<MarketBarProvider> providers) {
    return providers.stream()
        .collect(Collectors.toMap(MarketBarProvider::name, Function.identity()));
  }

  private static void pause() {
    try {
      Thread.sleep(300);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
    }
  }

  private static long oldestMillis(String timeframe, List<MarketBar> fetched) {
    return fetched.stream()
        .mapToLong(item -> barTimeMillis(timeframe, item.barTime()))
        .min()
        .orElse(Long.MAX_VALUE);
  }

  private static long barTimeMillis(String timeframe, String value) {
    if ("1D".equalsIgnoreCase(timeframe)) {
      return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
    }
    return Instant.parse(value).toEpochMilli();
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  private static long latestLookbackMillis(String timeframe) {
    long days = "1D".equalsIgnoreCase(timeframe) ? 14 : 5;
    return TimeUnit.DAYS.toMillis(days);
  }

  public record RefreshResult(String symbol, String timeframe, int fetched, boolean skipped) {
  }

  public record BackfillResult(
      String symbol,
      String timeframe,
      int fetched,
      int pages,
      boolean skipped) {
  }
}
