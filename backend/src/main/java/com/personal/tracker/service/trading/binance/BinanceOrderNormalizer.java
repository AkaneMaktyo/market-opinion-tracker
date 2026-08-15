package com.personal.tracker.service.trading.binance;

import com.personal.tracker.service.trading.binance.BinanceSpotClient.SymbolRules;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BinanceOrderNormalizer {
  private BinanceOrderNormalizer() {
  }

  public static NormalizedOrder normalize(
      BigDecimal requestedPrice,
      BigDecimal quoteCost,
      SymbolRules rules) {
    if (requestedPrice == null || requestedPrice.signum() <= 0) {
      throw new IllegalArgumentException("买入价格必须大于 0");
    }
    if (quoteCost == null || quoteCost.signum() <= 0) {
      throw new IllegalArgumentException("每批投入成本必须大于 0");
    }
    BigDecimal price = floorStep(requestedPrice, rules.tickSize());
    BigDecimal quantity = floorStep(quoteCost.divide(price, 24, RoundingMode.DOWN), rules.stepSize());
    if (quantity.compareTo(rules.minQuantity()) < 0) {
      throw new IllegalArgumentException("投入金额低于币安最小下单数量");
    }
    if (rules.maxQuantity().signum() > 0 && quantity.compareTo(rules.maxQuantity()) > 0) {
      throw new IllegalArgumentException("下单数量超过币安允许上限");
    }
    BigDecimal notional = price.multiply(quantity);
    if (notional.compareTo(rules.minNotional()) < 0) {
      throw new IllegalArgumentException("每批投入金额低于币安最小名义金额 "
          + rules.minNotional().stripTrailingZeros().toPlainString() + " " + rules.quoteAsset());
    }
    return new NormalizedOrder(price, quantity, notional);
  }

  static BigDecimal floorStep(BigDecimal value, BigDecimal step) {
    if (step == null || step.signum() <= 0) {
      return value.stripTrailingZeros();
    }
    return value.divide(step, 0, RoundingMode.DOWN).multiply(step).stripTrailingZeros();
  }

  public record NormalizedOrder(
      BigDecimal price,
      BigDecimal quantity,
      BigDecimal notional) {
  }
}
