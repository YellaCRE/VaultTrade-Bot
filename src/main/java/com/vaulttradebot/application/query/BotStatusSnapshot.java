package com.vaulttradebot.application.query;

import com.vaulttradebot.domain.ops.BotRunState;

import java.time.Instant;

public record BotStatusSnapshot(
        BotRunState state,
        Instant lastCycleAt,
        String lastError,
        int consecutiveFailures
) {
}
