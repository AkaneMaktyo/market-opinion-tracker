package com.personal.tracker.domain.celebrity.alerts;

import java.math.BigDecimal;
import java.util.List;

public record CelebrityAlertSettings(
    boolean enabled,
    List<String> investorSlugs,
    BigDecimal minimumReportedWeight,
    String updatedAt) {
}
