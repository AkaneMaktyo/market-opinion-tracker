package com.personal.tracker.domain.celebrity;

import java.math.BigDecimal;

public record CelebrityHoldingView(
    String holdingKey,
    String symbol,
    String symbolConfidence,
    String cusip,
    String issuerName,
    String titleClass,
    String putCall,
    BigDecimal shares,
    BigDecimal reportedValue,
    BigDecimal reportedWeight,
    BigDecimal reportedUnitValue,
    BigDecimal currentPrice,
    BigDecimal currentValue,
    BigDecimal currentWeight,
    BigDecimal estimatedAverageCost,
    BigDecimal estimatedCostLow,
    BigDecimal estimatedCostHigh,
    BigDecimal estimatedTotalCost,
    BigDecimal estimatedPnl,
    BigDecimal estimatedPnlPercent,
    String costMethod,
    String costConfidence,
    String costNote,
    String reportDate,
    String filedAt,
    String sourceUrl,
    String priceUpdatedAt) {
}
