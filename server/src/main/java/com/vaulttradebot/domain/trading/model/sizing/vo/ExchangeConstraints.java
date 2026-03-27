package com.vaulttradebot.domain.trading.model.sizing.vo;

import java.math.BigDecimal;

/** Exchange-level quantity and notional constraints. */
public record ExchangeConstraints(
        BigDecimal minNotionalKrw,
        BigDecimal minQuantity,
        BigDecimal maxQuantity,
        BigDecimal quantityStep,
        BigDecimal priceTick
) {
    public ExchangeConstraints {
        if (minNotionalKrw == null || minQuantity == null || maxQuantity == null
                || quantityStep == null || priceTick == null) {
            throw new IllegalArgumentException("exchange constraints must not be null");
        }
        if (minNotionalKrw.signum() < 0 || minQuantity.signum() < 0 || maxQuantity.signum() <= 0
                || quantityStep.signum() <= 0 || priceTick.signum() <= 0) {
            throw new IllegalArgumentException("exchange constraints contain invalid numeric values");
        }
        if (maxQuantity.compareTo(minQuantity) < 0) {
            throw new IllegalArgumentException("maxQuantity must be >= minQuantity");
        }
    }
}
