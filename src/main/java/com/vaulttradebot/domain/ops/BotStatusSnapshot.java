package com.vaulttradebot.domain.ops;

import java.time.Instant;

public record BotStatusSnapshot(
        BotRunState state,
        Instant lastCycleAt,
        String lastError,
        int consecutiveFailures
) {
}
