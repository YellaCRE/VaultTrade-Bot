package com.vaulttradebot.domain.shared.bot;

public record MetricsSnapshot(long successfulCycles, long failedCycles, double failureRate) {
}
