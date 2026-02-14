package com.vaulttradebot.domain.common.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Quantity(BigDecimal value) {
    public static final int SCALE = 8;

    public Quantity {
        if (value == null) {
            throw new IllegalArgumentException("quantity must not be null");
        }
        value = value.setScale(SCALE, RoundingMode.HALF_UP);
        if (value.signum() < 0) {
            throw new IllegalArgumentException("quantity must be >= 0");
        }
    }

    public static Quantity of(BigDecimal value) {
        return new Quantity(value);
    }

    public static Quantity zero() {
        return new Quantity(BigDecimal.ZERO);
    }

    public Quantity add(Quantity other) {
        requireOther(other);
        return new Quantity(value.add(other.value));
    }

    public Quantity subtract(Quantity other) {
        requireOther(other);
        BigDecimal result = value.subtract(other.value);
        if (result.signum() < 0) {
            throw new IllegalArgumentException("quantity cannot be negative");
        }
        return new Quantity(result);
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    private static void requireOther(Quantity other) {
        if (other == null) {
            throw new IllegalArgumentException("quantity must not be null");
        }
    }
}
