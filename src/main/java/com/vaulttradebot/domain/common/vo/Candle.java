package com.vaulttradebot.domain.common.vo;

import java.time.Instant;

public record Candle(
        Instant openTime,
        Price open,
        Price high,
        Price low,
        Price close,
        Quantity volume
) {
    public Candle {
        if (openTime == null || open == null || high == null || low == null || close == null || volume == null) {
            throw new IllegalArgumentException("candle fields must not be null");
        }
        if (!open.unitCurrency().equals(high.unitCurrency())
                || !open.unitCurrency().equals(low.unitCurrency())
                || !open.unitCurrency().equals(close.unitCurrency())) {
            throw new IllegalArgumentException("all candle prices must share the same unit currency");
        }
        if (high.value().compareTo(low.value()) < 0) {
            throw new IllegalArgumentException("high must be >= low");
        }
        if (open.value().compareTo(low.value()) < 0 || open.value().compareTo(high.value()) > 0) {
            throw new IllegalArgumentException("open must be within [low, high]");
        }
        if (close.value().compareTo(low.value()) < 0 || close.value().compareTo(high.value()) > 0) {
            throw new IllegalArgumentException("close must be within [low, high]");
        }
    }
}
