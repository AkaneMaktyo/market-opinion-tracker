package com.personal.tracker.service.celebrity;

import static org.assertj.core.api.Assertions.assertThat;

import com.personal.tracker.domain.celebrity.CelebrityHolding;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CelebrityCostEstimatorTest {
  private final CelebrityCostEstimator estimator = new CelebrityCostEstimator();

  @Test
  void rebuildsCostFromObservedQuarterlyIncreaseWithoutTreatingMissingPriceAsZero() {
    var estimate = estimator.estimate(List.of(
        holding("2025-12-31", "100", "10"),
        holding("2026-03-31", "150", "20")), "SEC_13F", new BigDecimal("25"));

    assertThat(estimate.averageCost()).isEqualByComparingTo("13.33333333");
    assertThat(estimate.costLow()).isEqualByComparingTo("10.00000000");
    assertThat(estimate.costHigh()).isEqualByComparingTo("13.33333333");
    assertThat(estimate.totalCost()).isEqualByComparingTo("2000.00");
    assertThat(estimate.pnl()).isEqualByComparingTo("1750.00");
    assertThat(estimate.pnlPercent()).isEqualByComparingTo("87.50");
    assertThat(estimate.confidence()).isEqualTo("LOW");
  }

  @Test
  void keepsCostAndPnlUnknownForSingleDisclosure() {
    var estimate = estimator.estimate(List.of(holding("2026-03-31", "100", "10")), "SEC_13F", null);

    assertThat(estimate.averageCost()).isNull();
    assertThat(estimate.pnl()).isNull();
    assertThat(estimate.confidence()).isEqualTo("UNKNOWN");
  }

  private static CelebrityHolding holding(String date, String shares, String unitValue) {
    BigDecimal quantity = new BigDecimal(shares);
    BigDecimal price = new BigDecimal(unitValue);
    return new CelebrityHolding(date, date, "druckenmiller", "ABC|COMMON|", "ABC", "HIGH", "000000000",
        "Example Inc", "COMMON", "", quantity, quantity.multiply(price), BigDecimal.ONE, price);
  }
}
