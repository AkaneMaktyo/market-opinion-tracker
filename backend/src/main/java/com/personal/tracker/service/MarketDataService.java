package com.personal.tracker.service;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.MarketBar;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.MarketBarRepository;
import com.personal.tracker.service.market.MarketBarProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MarketDataService {
  private static final int LIMIT = 1000;
  private static final int DEEP_BACKFILL_MAX_PAGES = 80;

  private final InstrumentRepository instruments;
  private final MarketBarRepository bars;
  private final List<MarketBarProvider> providers;

  public MarketDataService(
      InstrumentRepository instruments,
      MarketBarRepository bars,
      List<MarketBarProvider> providers) {
    this.instruments = instruments;
    this.bars = bars;
    this.providers = providers;
  }

  public List<MarketBar> barsForSymbol(String symbol, String timeframe) {
    Instrument instrument = instruments.saveIfAbsent(symbol, symbol, "US", null);
    String frame = timeframe == null || timeframe.isBlank() ? "1D" : timeframe;
    List<MarketBar> stored = bars.findBars(instrument.id(), frame);
    if (!stored.isEmpty()) {
      return stored;
    }
    for (MarketBarProvider provider : providers) {
      List<MarketBar> fetched = provider.fetch(instrument, frame);
      if (!fetched.isEmpty()) {
        bars.saveAll(fetched);
        return bars.findBars(instrument.id(), frame);
      }
    }
    return List.of();
  }

  public List<Instrument> instruments() {
    return instruments.findAll(null);
  }

  public Instrument instrument(String symbol) {
    return instruments.saveIfAbsent(symbol, symbol, "US", null);
  }

  public RefreshResult refreshBars(Instrument instrument, String timeframe) {
    String frame = timeframe == null || timeframe.isBlank() ? "1D" : timeframe;
    List<MarketBar> fetched = fetch(instrument, frame, null, null);
    if (fetched.isEmpty()) {
      return new RefreshResult(instrument.symbol(), frame, 0, true);
    }
    bars.saveAll(fetched);
    pause();
    return new RefreshResult(instrument.symbol(), frame, fetched.size(), false);
  }

  public BackfillResult deepBackfillBars(Instrument instrument, String timeframe) {
    String frame = timeframe == null || timeframe.isBlank() ? "1D" : timeframe;
    long cursor = Instant.now().toEpochMilli();
    long oldestSeen = Long.MAX_VALUE;
    int fetchedTotal = 0;
    int pages = 0;
    for (int page = 0; page < DEEP_BACKFILL_MAX_PAGES; page++) {
      List<MarketBar> fetched = fetch(instrument, frame, null, cursor);
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

  private List<MarketBar> fetch(Instrument instrument, String timeframe, Long start, Long end) {
    for (MarketBarProvider provider : providers) {
      List<MarketBar> fetched = provider.fetch(instrument, timeframe, start, end, LIMIT);
      if (!fetched.isEmpty()) {
        return fetched;
      }
    }
    return List.of();
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
