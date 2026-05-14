package com.personal.tracker.domain;

import java.math.BigDecimal;

public record Review(
    String id,
    String opinionId,
    String outcome,
    String notes,
    BigDecimal resultPrice,
    String reviewDate,
    String createdAt) {
}
