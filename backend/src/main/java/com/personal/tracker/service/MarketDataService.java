package com.personal.tracker.service;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.MarketBar;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.repository.MarketBarRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Service;

@Service
public class MarketDataService {
  private final InstrumentRepository instruments;
  private final MarketBarRepository bars;

  public MarketDataService(InstrumentRepository instruments, MarketBarRepository bars) {
    this.instruments = instruments;
    this.bars = bars;
  }

  public List<MarketBar> barsForSymbol(String symbol, String timeframe) {
    Instrument instrument = instruments.saveIfAbsent(symbol, symbol, "US", null);
    String frame = timeframe == null || timeframe.isBlank() ? "1D" : timeframe;
    if (bars.count(instrument.id(), frame) == 0) {
      bars.saveAll(generateBars(instrument, frame));
    }
    return bars.findBars(instrument.id(), frame);
  }

  private List<MarketBar> generateBars(Instrument instrument, String timeframe) {
    List<MarketBar> items = new ArrayList<>();
    Random random = new Random(instrument.symbol().hashCode());
    double price = 80 + Math.abs(instrument.symbol().hashCode() % 320);
    LocalDate date = LocalDate.now().minusDays(220);
    while (items.size() < 160) {
      if (date.getDayOfWeek().getValue() <= 5) {
        double drift = (random.nextDouble() - 0.45) * 4.0;
        double open = Math.max(5, price + drift);
        double close = Math.max(5, open + (random.nextDouble() - 0.48) * 6.0);
        double high = Math.max(open, close) + random.nextDouble() * 3.5;
        double low = Math.min(open, close) - random.nextDouble() * 3.5;
        double volume = 1_000_000 + random.nextDouble() * 9_000_000;
        items.add(new MarketBar(
            JdbcSupport.id(),
            instrument.id(),
            timeframe,
            date.toString(),
            money(open),
            money(high),
            money(low),
            money(close),
            money(volume)));
        price = close;
      }
      date = date.plusDays(1);
    }
    return items;
  }

  private static BigDecimal money(double value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
  }
}
