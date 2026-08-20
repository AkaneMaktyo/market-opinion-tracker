package com.personal.tracker.service.celebrity;

import com.personal.tracker.domain.celebrity.CelebrityHolding;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CelebrityCostEstimator {
  private static final int SCALE = 8;

  public CostEstimate estimate(
      List<CelebrityHolding> history,
      String sourceType,
      BigDecimal currentPrice) {
    if (history == null || history.size() < 2 || isOption(history.get(history.size() - 1))) {
      return CostEstimate.unknown(history == null || history.isEmpty()
          ? "暂无可用的历史披露"
          : "至少需要两期可比公开持仓，不能把单期市值当成真实成本");
    }
    BigDecimal quantity = BigDecimal.ZERO;
    BigDecimal totalCost = BigDecimal.ZERO;
    BigDecimal totalLow = BigDecimal.ZERO;
    BigDecimal totalHigh = BigDecimal.ZERO;
    BigDecimal previousUnitValue = null;
    for (CelebrityHolding snapshot : history) {
      BigDecimal shares = positive(snapshot.shares());
      BigDecimal unitValue = positive(snapshot.reportedUnitValue());
      if (shares == null || unitValue == null) {
        continue;
      }
      if (quantity.signum() == 0) {
        quantity = shares;
        totalCost = shares.multiply(unitValue);
        totalLow = totalCost;
        totalHigh = totalCost;
        previousUnitValue = unitValue;
        continue;
      }
      BigDecimal difference = shares.subtract(quantity);
      if (difference.signum() > 0) {
        BigDecimal lower = previousUnitValue == null ? unitValue : previousUnitValue.min(unitValue);
        BigDecimal upper = previousUnitValue == null ? unitValue : previousUnitValue.max(unitValue);
        totalCost = totalCost.add(difference.multiply(unitValue));
        totalLow = totalLow.add(difference.multiply(lower));
        totalHigh = totalHigh.add(difference.multiply(upper));
      } else if (difference.signum() < 0 && quantity.signum() > 0) {
        BigDecimal remainingRatio = shares.divide(quantity, SCALE, RoundingMode.HALF_UP);
        totalCost = totalCost.multiply(remainingRatio);
        totalLow = totalLow.multiply(remainingRatio);
        totalHigh = totalHigh.multiply(remainingRatio);
      }
      quantity = shares;
      previousUnitValue = unitValue;
    }
    if (quantity.signum() <= 0 || totalCost.signum() <= 0) {
      return CostEstimate.unknown("公开持仓变动不足以重建成本");
    }
    BigDecimal average = divide(totalCost, quantity);
    BigDecimal averageLow = divide(totalLow, quantity);
    BigDecimal averageHigh = divide(totalHigh, quantity);
    BigDecimal pnl = null;
    BigDecimal pnlPct = null;
    if (positive(currentPrice) != null) {
      pnl = currentPrice.subtract(average).multiply(quantity).setScale(2, RoundingMode.HALF_UP);
      pnlPct = currentPrice.subtract(average).divide(average, SCALE, RoundingMode.HALF_UP)
          .movePointRight(2).setScale(2, RoundingMode.HALF_UP);
    }
    boolean ark = "ARK_DAILY".equals(sourceType);
    return new CostEstimate(
        average, averageLow, averageHigh, totalCost.setScale(2, RoundingMode.HALF_UP), pnl, pnlPct,
        ark ? "ARK_DAILY_RECONSTRUCTION" : "SEC_13F_RECONSTRUCTION",
        ark ? "MEDIUM" : "LOW",
        ark
            ? "按已留存的 ARK 日度仓位变动与当日持仓市值重建，非基金实际成交成本"
            : "按连续 13F 季末仓位与市值重建，首期持仓与季内交易不可见，非真实成交成本");
  }

  private static boolean isOption(CelebrityHolding holding) {
    return holding.putCall() != null && !holding.putCall().isBlank();
  }

  private static BigDecimal positive(BigDecimal value) {
    return value != null && value.signum() > 0 ? value : null;
  }

  private static BigDecimal divide(BigDecimal amount, BigDecimal quantity) {
    return amount.divide(quantity, SCALE, RoundingMode.HALF_UP);
  }

  public record CostEstimate(
      BigDecimal averageCost,
      BigDecimal costLow,
      BigDecimal costHigh,
      BigDecimal totalCost,
      BigDecimal pnl,
      BigDecimal pnlPercent,
      String method,
      String confidence,
      String note) {
    static CostEstimate unknown(String note) {
      return new CostEstimate(null, null, null, null, null, null, "UNKNOWN", "UNKNOWN", note);
    }
  }
}
