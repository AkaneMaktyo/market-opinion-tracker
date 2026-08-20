package com.personal.tracker.domain.celebrity;

public record CelebrityFiling(
    String id,
    String investorId,
    String sourceType,
    String externalId,
    String formType,
    String reportDate,
    String filedAt,
    String sourceUrl,
    boolean amendment,
    String fetchedAt) {
}
