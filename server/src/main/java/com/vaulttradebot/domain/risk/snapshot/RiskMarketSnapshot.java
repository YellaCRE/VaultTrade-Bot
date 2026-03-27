package com.vaulttradebot.domain.risk.snapshot;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public record RiskMarketSnapshot(
        String marketSymbol,
        BigDecimal lastPriceKrw,
        BigDecimal bestBidKrw,
        BigDecimal bestAskKrw,
        BigDecimal volatilityRatio,
        Instant asOf,
        Duration staleAfter
) {
    public RiskMarketSnapshot {
        if (marketSymbol == null || marketSymbol.isBlank() || lastPriceKrw == null || bestBidKrw == null
                || bestAskKrw == null || volatilityRatio == null || asOf == null || staleAfter == null) {
            throw new IllegalArgumentException("market snapshot fields must not be null or blank");
        }
        if (lastPriceKrw.signum() <= 0 || bestBidKrw.signum() <= 0 || bestAskKrw.signum() <= 0 || volatilityRatio.signum() < 0) {
            throw new IllegalArgumentException("market snapshot numeric values out of range");
        }
        if (staleAfter.isNegative()) {
            throw new IllegalArgumentException("staleAfter must not be negative");
        }
    }
}
