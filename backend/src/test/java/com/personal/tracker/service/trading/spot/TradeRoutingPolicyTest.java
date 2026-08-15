package com.personal.tracker.service.trading.spot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TradeRoutingPolicyTest {
  private final TradeRoutingPolicy routing = new TradeRoutingPolicy();

  @Test
  void routesCryptoToBinanceSpot() {
    var route = routing.route("CRYPTO", "BTC");

    assertEquals("CRYPTO", route.assetClass());
    assertEquals("BINANCE", route.provider());
    assertTrue(route.supported());
  }

  @Test
  void routesStocksToBinanceStocks() {
    var route = routing.route("US", "GOOGL");

    assertEquals("STOCK", route.assetClass());
    assertEquals("BINANCE_STOCKS", route.provider());
    assertTrue(route.supported());
  }
}
