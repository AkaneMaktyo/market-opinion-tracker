package com.personal.tracker.web;

import com.personal.tracker.domain.MarketBar;
import com.personal.tracker.service.MarketDataService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketController {
  private final MarketDataService marketData;

  public MarketController(MarketDataService marketData) {
    this.marketData = marketData;
  }

  @GetMapping("/{symbol}/bars")
  List<MarketBar> bars(
      @PathVariable String symbol,
      @RequestParam(defaultValue = "1D") String timeframe) {
    return marketData.barsForSymbol(symbol, timeframe);
  }
}
