package com.vaulttradebot.application.query;

import java.time.OffsetDateTime;

public record SchedulerStatusSnapshot(
        boolean enabled,
        boolean paused,
        boolean executionInProgress,
        String cron,
        String zoneId,
        String misfirePolicy,
        OffsetDateTime nextPlannedAt,
        OffsetDateTime pendingRetryAt,
        int pendingRetryAttempt,
        OffsetDateTime lastScheduledAt,
        OffsetDateTime lastStartedAt,
        OffsetDateTime lastCompletedAt,
        OffsetDateTime lastSuccessAt,
        OffsetDateTime lastFailureAt,
        String lastTrigger,
        String lastMessage,
        long totalDispatches,
        long totalSuccesses,
        long totalFailures,
        long totalMisfires,
        long totalOverlapPreventions
) {
}
