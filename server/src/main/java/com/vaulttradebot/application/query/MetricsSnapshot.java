package com.vaulttradebot.application.query;

import java.util.Map;

public record MetricsSnapshot(
        long successfulCycles,
        long failedCycles,
        double failureRate,
        long riskDecisionTotal,
        long riskAllowCount,
        long riskRejectCount,
        long riskAllowWithLimitCount,
        Map<String, Long> riskReasonCodeCounts,
        Map<String, Long> riskDecisionTypeCounts
) {
}
