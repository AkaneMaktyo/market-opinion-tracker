package com.personal.tracker.service.market;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class BitgetMarketBarProviderTest {
  @Test
  void includesBitgetStockBoardCandidatesForEquities() {
    List<BitgetMarketBarProvider.Query> queries = BitgetMarketBarProvider.queriesForSymbol("NOK");

    assertEquals(List.of(
        new BitgetMarketBarProvider.Query("USDT-FUTURES", "NOKUSDT"),
        new BitgetMarketBarProvider.Query("USDT-FUTURES", "NOKSTOCKUSDT"),
        new BitgetMarketBarProvider.Query("SPOT", "RNOKUSDT"),
        new BitgetMarketBarProvider.Query("SPOT", "NOKONUSDT"),
        new BitgetMarketBarProvider.Query("SPOT", "NOKUSDT")), queries);
  }

  @Test
  void normalizesSymbolsThatAlreadyContainUsdtSuffix() {
    List<BitgetMarketBarProvider.Query> queries = BitgetMarketBarProvider.queriesForSymbol("aaplusdt");

    assertEquals(List.of(
        new BitgetMarketBarProvider.Query("USDT-FUTURES", "AAPLUSDT"),
        new BitgetMarketBarProvider.Query("USDT-FUTURES", "AAPLSTOCKUSDT"),
        new BitgetMarketBarProvider.Query("SPOT", "RAAPLUSDT"),
        new BitgetMarketBarProvider.Query("SPOT", "AAPLONUSDT"),
        new BitgetMarketBarProvider.Query("SPOT", "AAPLUSDT")), queries);
  }
}
