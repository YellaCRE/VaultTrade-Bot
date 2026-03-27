package com.vaulttradebot.domain.execution.vo;

import java.util.Objects;
import java.util.UUID;

public record OrderId(String value) {
    public OrderId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("order id must not be blank");
        }
    }

    /** Generates a new random order identifier. */
    public static OrderId random() {
        return new OrderId(UUID.randomUUID().toString());
    }

    /** Creates an OrderId from an existing string value. */
    public static OrderId of(String value) {
        return new OrderId(value);
    }

    /** Returns the raw identifier value. */
    @Override
    public String toString() {
        return Objects.requireNonNull(value);
    }
}
