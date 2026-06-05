package com.personal.tracker.service.positions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PositionActionResolverTest {
  private final PositionActionResolver resolver = new PositionActionResolver();

  @Test
  void bearishViewDoesNotClosePosition() {
    assertEquals("IGNORE", resolver.resolve("", "看空", "估值偏高", "", "", "", ""));
  }

  @Test
  void explicitSellClosesPosition() {
    assertEquals("CLOSE", resolver.resolve("", "看多", "卖出 NVDA 锁定利润", "", "", "", ""));
  }

  @Test
  void explicitBuyOrHoldOpensPosition() {
    assertEquals("OPEN", resolver.resolve("", "观望", "继续持有 BTC", "", "", "", ""));
  }

  @Test
  void explicitIgnoreIsNotOverriddenByRiskWords() {
    assertEquals("IGNORE", resolver.resolve("IGNORE", "看多", "止损位在 100", "", "", "", ""));
  }

  @Test
  void riskOnlyStopLossDoesNotClosePosition() {
    assertEquals("IGNORE", resolver.resolve("", "看多", "继续观察", "", "跌破 100 止损", "", ""));
  }
}
