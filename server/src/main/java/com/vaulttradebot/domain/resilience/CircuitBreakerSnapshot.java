package com.vaulttradebot.domain.resilience;

import java.time.Instant;

public record CircuitBreakerSnapshot(
        String name,
        CircuitBreakerState state,
        int consecutiveFailures,
        Instant openedAt,
        Instant nextAttemptAt
) {
}
