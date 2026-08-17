package com.personal.tracker.service.positions;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record PositionStatsView(
    String kolId,
    int totalTrades,
    int settledTrades,
    int wins,
    int losses,
    BigDecimal winRate,
    BigDecimal avgPnlPct,
    BigDecimal bestPnlPct,
    BigDecimal worstPnlPct,
    BigDecimal totalPnlPct,
    int activeCount) {

  public static PositionStatsView empty(String kolId, int activeCount) {
    return new PositionStatsView(kolId, 0, 0, 0, 0, null, null, null, null, null, activeCount);
  }

  public static PositionStatsView of(
      String kolId,
      long totalTrades,
      long settledTrades,
      long wins,
      long losses,
      BigDecimal avgPnlPct,
      BigDecimal bestPnlPct,
      BigDecimal worstPnlPct,
      BigDecimal totalPnlPct,
      int activeCount) {
    BigDecimal winRate = settledTrades > 0
        ? BigDecimal.valueOf(wins)
            .divide(BigDecimal.valueOf(settledTrades), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
        : null;
    return new PositionStatsView(
        kolId,
        (int) totalTrades,
        (int) settledTrades,
        (int) wins,
        (int) losses,
        winRate,
        avgPnlPct,
        bestPnlPct,
        worstPnlPct,
        totalPnlPct,
        activeCount);
  }
}
