package com.personal.tracker.web;

import com.personal.tracker.domain.MarketBar;
import com.personal.tracker.service.MarketDataBackfillService;
import com.personal.tracker.service.MarketDataBackfillService.BackfillStatus;
import com.personal.tracker.service.MarketDataService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketController {
  private final MarketDataService marketData;
  private final MarketDataBackfillService backfill;

  public MarketController(MarketDataService marketData, MarketDataBackfillService backfill) {
    this.marketData = marketData;
    this.backfill = backfill;
  }

  @GetMapping("/{symbol}/bars")
  List<MarketBar> bars(
      @PathVariable String symbol,
      @RequestParam(defaultValue = "1D") String timeframe) {
    return marketData.barsForSymbol(symbol, timeframe);
  }

  @PostMapping("/backfill")
  BackfillStatus startBackfill() {
    return backfill.startAll();
  }

  @PostMapping("/{symbol}/backfill")
  BackfillStatus startSymbolBackfill(@PathVariable String symbol) {
    return backfill.startSymbol(symbol);
  }

  @GetMapping("/backfill")
  BackfillStatus backfillStatus() {
    return backfill.status();
  }
}
