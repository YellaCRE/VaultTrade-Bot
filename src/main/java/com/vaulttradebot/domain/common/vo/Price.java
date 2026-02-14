package com.vaulttradebot.domain.common.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Price(BigDecimal value) {
    public Price {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("price value must be positive");
        }
        value = value.setScale(8, RoundingMode.HALF_UP);
    }

    public static Price of(BigDecimal value) {
        return new Price(value);
    }
}
