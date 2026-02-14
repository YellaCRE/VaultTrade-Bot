package com.vaulttradebot.domain.risk.snapshot;

import java.util.Map;

public record RiskMetricsSnapshot(
        long totalDecisions,
        long allowCount,
        long rejectCount,
        long allowWithLimitCount,
        Map<String, Long> reasonCodeCounts,
        Map<String, Long> decisionTypeCounts
) {
}
