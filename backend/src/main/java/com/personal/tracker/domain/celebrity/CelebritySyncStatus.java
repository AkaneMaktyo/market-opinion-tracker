package com.personal.tracker.domain.celebrity;

public record CelebritySyncStatus(
    boolean running,
    boolean enabled,
    String lastStartedAt,
    String lastCompletedAt,
    String lastOutcome,
    String lastError,
    int investorsSynced,
    int filingsSynced,
    int holdingsSynced) {
}
