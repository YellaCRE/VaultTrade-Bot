package com.vaulttradebot.domain.ops;

public record MetricsSnapshot(long successfulCycles, long failedCycles, double failureRate) {
}
