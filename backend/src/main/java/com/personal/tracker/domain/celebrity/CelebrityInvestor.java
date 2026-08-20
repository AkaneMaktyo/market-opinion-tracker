package com.personal.tracker.domain.celebrity;

public record CelebrityInvestor(
    String id,
    String slug,
    String displayName,
    String managerName,
    String sourceType,
    String cik,
    String sourceUrl,
    boolean enabled,
    String createdAt,
    String updatedAt) {
}
