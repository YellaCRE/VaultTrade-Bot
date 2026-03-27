package com.vaulttradebot.domain.common.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Price(BigDecimal value, Asset unitCurrency) {
    public Price {
        if (value == null) {
            throw new IllegalArgumentException("price value must not be null");
        }
        if (unitCurrency == null) {
            throw new IllegalArgumentException("price unitCurrency must not be null");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("price value must be >= 0");
        }
        value = value.setScale(8, RoundingMode.HALF_UP);
    }

    /** Creates a price in the given unit currency. */
    public static Price of(BigDecimal value, Asset unitCurrency) {
        return new Price(value, unitCurrency);
    }
}
