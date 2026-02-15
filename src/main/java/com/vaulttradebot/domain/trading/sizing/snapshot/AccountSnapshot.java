package com.vaulttradebot.domain.trading.sizing.snapshot;

import java.math.BigDecimal;

/** Available and reserved balances used for final sizing checks. */
public record AccountSnapshot(
        BigDecimal availableQuoteKrw,
        BigDecimal availableBaseQuantity,
        BigDecimal reservedQuoteKrw,
        BigDecimal reservedBaseQuantity,
        BigDecimal currentBaseQuantity,
        BigDecimal sameSideOpenOrderQuantity
) {
    public AccountSnapshot {
        if (availableQuoteKrw == null || availableBaseQuantity == null || reservedQuoteKrw == null
                || reservedBaseQuantity == null || currentBaseQuantity == null || sameSideOpenOrderQuantity == null) {
            throw new IllegalArgumentException("account snapshot fields must not be null");
        }
        if (availableQuoteKrw.signum() < 0 || availableBaseQuantity.signum() < 0 || reservedQuoteKrw.signum() < 0
                || reservedBaseQuantity.signum() < 0 || currentBaseQuantity.signum() < 0
                || sameSideOpenOrderQuantity.signum() < 0) {
            throw new IllegalArgumentException("account snapshot numeric values must be >= 0");
        }
    }
}
