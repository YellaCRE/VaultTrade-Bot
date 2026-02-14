package com.vaulttradebot.domain.common.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Price(BigDecimal value, AssetSymbol unitCurrency) {
    public static final int SCALE = 8;

    public Price {
        if (value == null) {
            throw new IllegalArgumentException("price value must not be null");
        }
        if (unitCurrency == null) {
            throw new IllegalArgumentException("unit currency must not be null");
        }
        value = value.setScale(SCALE, RoundingMode.HALF_UP);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("price value must be > 0");
        }
    }

    public static Price of(BigDecimal value, AssetSymbol unitCurrency) {
        return new Price(value, unitCurrency);
    }

    public BigDecimal notionalOf(Quantity quantity) {
        if (quantity == null) {
            throw new IllegalArgumentException("quantity must not be null");
        }
        return value.multiply(quantity.value());
    }

    public void validateFor(Market market) {
        if (market == null) {
            throw new IllegalArgumentException("market must not be null");
        }
        if (!unitCurrency.equals(market.quote())) {
            throw new IllegalArgumentException("price unit currency must match market quote");
        }
    }
}
