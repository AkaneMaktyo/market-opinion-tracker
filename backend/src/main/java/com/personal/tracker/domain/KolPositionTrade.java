package com.personal.tracker.domain;

import java.math.BigDecimal;

public record KolPositionTrade(
    String id,
    String kolId,
    String instrumentId,
    String symbol,
    String direction,
    BigDecimal entryPrice,
    String entryAt,
    String entryOpinionId,
    BigDecimal exitPrice,
    String exitAt,
    String exitOpinionId,
    String exitReason,
    BigDecimal pnlPct,
    String createdAt) {

  public static BigDecimal pnlPct(
      String direction, BigDecimal entryPrice, BigDecimal exitPrice) {
    if (entryPrice == null || exitPrice == null
        || entryPrice.signum() <= 0) {
      return null;
    }
    BigDecimal change = exitPrice.subtract(entryPrice)
        .divide(entryPrice, 10, java.math.RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100));
    return "SHORT".equals(direction) ? change.negate() : change;
  }
}
