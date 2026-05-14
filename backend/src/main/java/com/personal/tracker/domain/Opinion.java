package com.personal.tracker.domain;

import java.math.BigDecimal;

public record Opinion(
    String id,
    String sessionId,
    String instrumentId,
    String symbol,
    String direction,
    String horizon,
    String thesis,
    String triggerCondition,
    String invalidation,
    Integer confidence,
    String sourceQuote,
    BigDecimal referencePrice,
    String rawDirection,
    String risksText,
    String catalystsText,
    String priceNotesText,
    String rawItemJson,
    String opinionTime,
    String status,
    String createdAt) {
}
