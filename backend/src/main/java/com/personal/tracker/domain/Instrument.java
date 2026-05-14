package com.personal.tracker.domain;

public record Instrument(
    String id,
    String symbol,
    String name,
    String market,
    String sector,
    String createdAt) {
}
