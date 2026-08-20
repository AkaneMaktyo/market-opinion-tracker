package com.personal.tracker.domain.celebrity;

import java.math.BigDecimal;

public record CelebrityInvestorOverview(
    String slug,
    String displayName,
    String managerName,
    String sourceType,
    String sourceUrl,
    String reportDate,
    String filedAt,
    String syncedAt,
    int disclosureDelayDays,
    int holdingCount,
    BigDecimal reportedPortfolioValue) {
}
