package com.vaulttradebot.domain.execution;

import java.util.UUID;

public record IdempotencyKey(String value) {
    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
    }

    public static IdempotencyKey random() {
        return new IdempotencyKey(UUID.randomUUID().toString());
    }
}
