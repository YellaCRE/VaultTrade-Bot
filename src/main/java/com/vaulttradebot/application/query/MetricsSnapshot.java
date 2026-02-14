package com.vaulttradebot.application.query;

public record MetricsSnapshot(
        long successfulCycles,
        long failedCycles,
        double failureRate
) {
}
