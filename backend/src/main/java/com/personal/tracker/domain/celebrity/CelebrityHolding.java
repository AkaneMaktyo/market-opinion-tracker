package com.personal.tracker.domain.celebrity;

import java.math.BigDecimal;

public record CelebrityHolding(
    String id,
    String filingId,
    String investorId,
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
    BigDecimal reportedUnitValue) {
}
