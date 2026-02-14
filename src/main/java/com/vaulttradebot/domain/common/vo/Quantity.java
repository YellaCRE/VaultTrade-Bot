package com.vaulttradebot.domain.common.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Quantity(BigDecimal value) {
    public Quantity {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("quantity value must be >= 0");
        }
        value = value.setScale(8, RoundingMode.HALF_UP);
    }

    /** Factory method for quantity creation and normalization. */
    public static Quantity of(BigDecimal value) {
        return new Quantity(value);
    }

    /** Returns a new quantity equal to this + other. */
    public Quantity add(Quantity other) {
        requireOther(other);
        return Quantity.of(value.add(other.value));
    }

    /** Returns a new quantity equal to this - other. */
    public Quantity subtract(Quantity other) {
        requireOther(other);
        BigDecimal result = value.subtract(other.value);
        if (result.signum() < 0) {
            throw new IllegalArgumentException("quantity cannot become negative");
        }
        return Quantity.of(result);
    }

    private void requireOther(Quantity other) {
        if (other == null) {
            throw new IllegalArgumentException("other quantity must not be null");
        }
    }
}
