package com.vaulttradebot.application.query;

import com.vaulttradebot.domain.ops.BotRunState;

import java.time.OffsetDateTime;

public record BotStatusSnapshot(
        BotRunState state,
        OffsetDateTime lastCycleAt,
        String lastError,
        int consecutiveFailures,
        OffsetDateTime killSwitchActivatedAt,
        String killSwitchReason
) {
}
