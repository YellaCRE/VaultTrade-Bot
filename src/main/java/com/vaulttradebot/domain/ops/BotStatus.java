package com.vaulttradebot.domain.ops;

import java.time.Instant;

public record BotStatus(
        boolean running,
        Instant lastCycleAt,
        String lastError
) {
}
