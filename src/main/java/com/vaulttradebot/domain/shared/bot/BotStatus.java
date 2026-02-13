package com.vaulttradebot.domain.shared.bot;

import java.time.Instant;

public record BotStatus(
        boolean running,
        Instant lastCycleAt,
        String lastError
) {
}
