package com.personal.tracker.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.domain.MarketBar;
import com.personal.tracker.service.MarketDataBackfillService;
import com.personal.tracker.service.MarketDataService;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketControllerTest {
  @Test
  void loadsBarsForSymbolAndTimeframe() {
    var marketData = mock(MarketDataService.class);
    var backfill = mock(MarketDataBackfillService.class);
    List<MarketBar> bars = List.of();
    when(marketData.barsForSymbol("BTC", "1H", 600, null)).thenReturn(bars);
    var controller = new MarketController(marketData, backfill);

    assertEquals(bars, controller.bars("BTC", "1H", 600, null));

    verify(marketData).barsForSymbol("BTC", "1H", 600, null);
  }
}
