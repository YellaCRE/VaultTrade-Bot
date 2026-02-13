package com.vaulttradebot.domain.shared.bot;

import java.time.Instant;

public record BotStatusSnapshot(
        BotRunState state,
        Instant lastCycleAt,
        String lastError,
        int consecutiveFailures
) {
}
