package com.personal.tracker.domain.celebrity;

import java.math.BigDecimal;

public record CelebrityHoldingChange(
    String holdingKey,
    String symbol,
    String issuerName,
    String action,
    BigDecimal currentShares,
    BigDecimal previousShares,
    BigDecimal sharesDelta,
    BigDecimal sharesChangePercent,
    BigDecimal reportedValue,
    BigDecimal reportedWeight,
    String reportDate,
    String filedAt,
    String sourceUrl) {
}
