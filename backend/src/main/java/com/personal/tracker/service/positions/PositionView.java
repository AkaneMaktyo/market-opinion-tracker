package com.personal.tracker.service.positions;

import com.personal.tracker.domain.KolPosition;
import java.math.BigDecimal;

public record PositionView(
    KolPosition position,
    BigDecimal lastPrice,
    BigDecimal pnlPct) {
}
