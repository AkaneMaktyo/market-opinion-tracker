package com.personal.tracker.domain;

public record KolPosition(
    String id,
    String kolId,
    String instrumentId,
    String symbol,
    String instrumentName,
    String status,
    String openedAt,
    String closedAt,
    String lastOpinionId,
    String lastAction,
    String createdAt,
    String updatedAt) {
}
