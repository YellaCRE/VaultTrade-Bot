package com.vaulttradebot.application.query;

import java.time.Instant;

public record SchedulerStatusSnapshot(
        boolean enabled,
        boolean paused,
        boolean executionInProgress,
        String cron,
        String zoneId,
        String misfirePolicy,
        Instant nextPlannedAt,
        Instant pendingRetryAt,
        int pendingRetryAttempt,
        Instant lastScheduledAt,
        Instant lastStartedAt,
        Instant lastCompletedAt,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        String lastTrigger,
        String lastMessage,
        long totalDispatches,
        long totalSuccesses,
        long totalFailures,
        long totalMisfires,
        long totalOverlapPreventions
) {
}
