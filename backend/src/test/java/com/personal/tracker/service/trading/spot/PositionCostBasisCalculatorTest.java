package com.personal.tracker.service.trading.spot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.personal.tracker.repository.trading.PositionCostOverrideRepository.CostAnchor;
import com.personal.tracker.repository.trading.SignalTradeRepository.PositionCost;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PositionCostBasisCalculatorTest {
  @Test
  void addsLaterApplicationTradesToManualBasis() {
    CostAnchor anchor = new CostAnchor(number("2"), number("120"), zero(), zero());
    PositionCost trades = trades("1", "90");

    var result = PositionCostBasisCalculator.fromAnchor(number("3"), anchor, trades);

    assertTrue(result.known());
    assertEquals(0, number("210").compareTo(result.cost()));
    assertEquals(0, number("70").compareTo(result.averageCost()));
  }

  @Test
  void waitsForExchangeBalanceBeforeApplyingNewTrade() {
    CostAnchor anchor = new CostAnchor(number("2"), number("120"), zero(), zero());
    PositionCost trades = trades("1", "90");

    var result = PositionCostBasisCalculator.fromAnchor(number("2"), anchor, trades);

    assertTrue(result.known());
    assertEquals(0, number("120").compareTo(result.cost()));
    assertEquals(0, number("60").compareTo(result.averageCost()));
    assertFalse(PositionCostBasisCalculator.canAdvanceAnchor(number("2"), anchor, trades));
  }

  @Test
  void keepsUnitCostWhenPositionIsReduced() {
    CostAnchor anchor = new CostAnchor(number("2"), number("120"), zero(), zero());

    var result = PositionCostBasisCalculator.fromAnchor(number("1.25"), anchor, null);

    assertTrue(result.known());
    assertEquals(0, number("75").compareTo(result.cost()));
    assertEquals(0, number("60").compareTo(result.averageCost()));
  }

  @Test
  void includesSmallBaseAssetFeeInAverageCost() {
    PositionCost trades = trades("1", "100");

    var result = PositionCostBasisCalculator.fromTrades(number("0.999"), trades);

    assertTrue(result.known());
    assertEquals(0, number("100").compareTo(result.cost()));
    assertEquals(0, number("100.1001001001001001").compareTo(result.averageCost()));
  }

  @Test
  void requiresReviewForUntrackedIncrease() {
    CostAnchor anchor = new CostAnchor(number("2"), number("120"), zero(), zero());

    var result = PositionCostBasisCalculator.fromAnchor(number("3"), anchor, null);

    assertFalse(result.known());
    assertTrue(result.reviewRequired());
    assertEquals(0, number("60").compareTo(result.averageCost()));
  }

  private static PositionCost trades(String quantity, String quote) {
    return new PositionCost(
        "BINANCE", "BTCUSDT", "BTC", "USDT", number(quantity), number(quote));
  }

  private static BigDecimal number(String value) {
    return new BigDecimal(value);
  }

  private static BigDecimal zero() {
    return BigDecimal.ZERO;
  }
}
