package com.personal.tracker.domain;

import java.math.BigDecimal;

public record PriceLevel(
    String id,
    String opinionId,
    String levelType,
    BigDecimal price,
    String note) {
}
