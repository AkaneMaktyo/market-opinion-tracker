package com.personal.tracker.domain;

import java.math.BigDecimal;

public record KolPosition(
    String id,
    String kolId,
    String instrumentId,
    String symbol,
    String instrumentName,
    String status,
    String direction,
    BigDecimal entryPrice,
    BigDecimal exitPrice,
    String exitReason,
    String openedAt,
    String closedAt,
    String lastOpinionId,
    String lastAction,
    String createdAt,
    String updatedAt) {
}
