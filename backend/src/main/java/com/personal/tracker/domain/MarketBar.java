package com.personal.tracker.domain;

import java.math.BigDecimal;

public record MarketBar(
    String id,
    String instrumentId,
    String timeframe,
    String barTime,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume) {
}
