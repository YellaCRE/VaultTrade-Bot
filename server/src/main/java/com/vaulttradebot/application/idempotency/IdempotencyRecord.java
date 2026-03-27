package com.vaulttradebot.application.idempotency;

import java.time.Instant;

public record IdempotencyRecord(
        String key,
        String requestHash,
        IdempotencySnapshot snapshot,
        Instant createdAt,
        Instant expiresAt
) {
    public boolean isExpired(Instant now) {
        // Record is expired when current time is past configured TTL deadline.
        return expiresAt != null && now != null && now.isAfter(expiresAt);
    }

    public boolean isCompleted() {
        // Snapshot existence means the first request already finished.
        return snapshot != null;
    }
}
