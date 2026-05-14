package com.personal.tracker.domain;

public record LiveSession(
    String id,
    String kolId,
    String sessionDate,
    String title,
    String source,
    String rawText,
    String createdAt) {
}
