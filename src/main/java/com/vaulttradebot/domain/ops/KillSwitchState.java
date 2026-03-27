package com.vaulttradebot.domain.ops;

import java.time.Instant;

public record KillSwitchState(
        Instant activatedAt,
        String reason
) {
    public KillSwitchState {
        if (activatedAt == null) {
            throw new IllegalArgumentException("activatedAt must not be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
