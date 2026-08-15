package com.personal.tracker.service.trading.spot;

import com.personal.tracker.repository.trading.PositionCostOverrideRepository.CostAnchor;
import com.personal.tracker.repository.trading.SignalTradeRepository.PositionCost;
import java.math.BigDecimal;
import java.math.RoundingMode;

final class PositionCostBasisCalculator {
  private static final BigDecimal MATCH_TOLERANCE = new BigDecimal("0.005");
  private static final BigDecimal MIN_TOLERANCE = new BigDecimal("0.00000001");
  private static final int SCALE = 16;

  private PositionCostBasisCalculator() {
  }

  static ReconciledCost fromAnchor(
      BigDecimal currentQuantity, CostAnchor anchor, PositionCost trades) {
    BigDecimal tradeQuantity = trades == null ? BigDecimal.ZERO : trades.executedQuantity();
    BigDecimal tradeQuote = trades == null ? BigDecimal.ZERO : trades.cumulativeQuote();
    BigDecimal additionalQuantity = positive(tradeQuantity.subtract(anchor.tradeQuantity()));
    BigDecimal additionalQuote = positive(tradeQuote.subtract(anchor.tradeQuote()));
    if (additionalQuantity.signum() > 0
        && quantitiesMatch(currentQuantity, anchor.basisQuantity())) {
      return reconcile(currentQuantity, anchor.basisQuantity(), anchor.basisCost());
    }
    return reconcile(
        currentQuantity,
        anchor.basisQuantity().add(additionalQuantity),
        anchor.basisCost().add(additionalQuote));
  }

  static ReconciledCost fromTrades(BigDecimal currentQuantity, PositionCost trades) {
    if (trades == null) return ReconciledCost.unknown();
    return reconcile(currentQuantity, trades.executedQuantity(), trades.cumulativeQuote());
  }

  static boolean canAdvanceAnchor(
      BigDecimal currentQuantity, CostAnchor anchor, PositionCost trades) {
    if (trades == null) return true;
    BigDecimal additionalQuantity = positive(
        trades.executedQuantity().subtract(anchor.tradeQuantity()));
    if (additionalQuantity.signum() <= 0) return true;
    return quantitiesMatch(
        currentQuantity, anchor.basisQuantity().add(additionalQuantity));
  }

  private static ReconciledCost reconcile(
      BigDecimal currentQuantity, BigDecimal trackedQuantity, BigDecimal trackedCost) {
    if (notPositive(currentQuantity) || notPositive(trackedQuantity) || notPositive(trackedCost)) {
      return ReconciledCost.unknown();
    }
    BigDecimal averageCost = divide(trackedCost, trackedQuantity);
    BigDecimal difference = currentQuantity.subtract(trackedQuantity);
    BigDecimal tolerance = tolerance(currentQuantity, trackedQuantity);
    if (difference.compareTo(tolerance) > 0) {
      return ReconciledCost.reviewRequired(averageCost);
    }
    if (difference.abs().compareTo(tolerance) <= 0) {
      return ReconciledCost.known(trackedCost, divide(trackedCost, currentQuantity));
    }
    return ReconciledCost.known(
        currentQuantity.multiply(averageCost), averageCost);
  }

  private static BigDecimal positive(BigDecimal value) {
    return value != null && value.signum() > 0 ? value : BigDecimal.ZERO;
  }

  private static boolean quantitiesMatch(BigDecimal left, BigDecimal right) {
    if (left == null || right == null) return false;
    return left.subtract(right).abs().compareTo(tolerance(left, right)) <= 0;
  }

  private static BigDecimal tolerance(BigDecimal left, BigDecimal right) {
    return left.max(right).multiply(MATCH_TOLERANCE).max(MIN_TOLERANCE);
  }

  private static boolean notPositive(BigDecimal value) {
    return value == null || value.signum() <= 0;
  }

  private static BigDecimal divide(BigDecimal left, BigDecimal right) {
    return left.divide(right, SCALE, RoundingMode.HALF_UP);
  }

  record ReconciledCost(
      boolean known, boolean reviewRequired, BigDecimal cost, BigDecimal averageCost) {
    static ReconciledCost known(BigDecimal cost, BigDecimal averageCost) {
      return new ReconciledCost(true, false, cost, averageCost);
    }

    static ReconciledCost reviewRequired(BigDecimal averageCost) {
      return new ReconciledCost(false, true, null, averageCost);
    }

    static ReconciledCost unknown() {
      return new ReconciledCost(false, false, null, null);
    }
  }
}
