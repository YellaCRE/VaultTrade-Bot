package com.vaulttradebot.domain.shared.market;

import java.math.BigDecimal;
import java.time.Instant;

public record Candle(
        Instant openTime,
        Price open,
        Price high,
        Price low,
        Price close,
        BigDecimal volume
) {
    public Candle {
        if (openTime == null || open == null || high == null || low == null || close == null || volume == null) {
            throw new IllegalArgumentException("candle fields must not be null");
        }
        if (volume.signum() < 0) {
            throw new IllegalArgumentException("volume must be >= 0");
        }
    }
}
