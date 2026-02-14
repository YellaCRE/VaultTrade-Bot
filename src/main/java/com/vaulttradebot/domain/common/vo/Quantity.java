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

    public static Quantity of(BigDecimal value) {
        return new Quantity(value);
    }
}
