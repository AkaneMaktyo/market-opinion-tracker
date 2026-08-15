package com.personal.tracker.service.trading.binance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.personal.tracker.service.trading.binance.BinanceSpotClient.SymbolRules;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BinanceOrderNormalizerTest {
  private final SymbolRules rules = new SymbolRules(
      "BTCUSDT", "TRADING", "BTC", "USDT",
      number("0.01"), number("0.001"), number("0.001"), number("100"), number("5"));

  @Test
  void floorsPriceAndQuantityToExchangeSteps() {
    var order = BinanceOrderNormalizer.normalize(number("100.009"), number("30"), rules);

    assertEquals(number("100"), order.price());
    assertEquals(number("0.3"), order.quantity());
    assertEquals(number("30"), order.notional());
  }

  @Test
  void rejectsCostBelowMinimumNotional() {
    assertThrows(IllegalArgumentException.class,
        () -> BinanceOrderNormalizer.normalize(number("100"), number("1"), rules));
  }

  private BigDecimal number(String value) {
    return new BigDecimal(value).stripTrailingZeros();
  }
}
