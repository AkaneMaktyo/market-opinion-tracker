package com.personal.tracker.domain;

public record Instrument(
    String id,
    String symbol,
    String name,
    String market,
    String sector,
    String groupName,
    String logoUrl,
    String bitgetCategory,
    String bitgetSymbol,
    String bitgetStatus,
    String bitgetCheckedAt,
    String createdAt) {
}
