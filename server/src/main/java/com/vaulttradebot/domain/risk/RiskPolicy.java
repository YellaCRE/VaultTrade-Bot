package com.vaulttradebot.domain.risk;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;

public record RiskPolicy(
        BigDecimal minOrderKrw,
        BigDecimal maxOrderKrw,
        BigDecimal maxExposureRatio,
        BigDecimal maxDailyLossRatio,
        Duration cooldown,
        Duration marketDataStaleAfter,
        ZoneId tradingDayZone,
        BigDecimal feeBufferRatio,
        BigDecimal slippageBufferRatio
) {
    public RiskPolicy {
        if (minOrderKrw == null || maxOrderKrw == null || maxExposureRatio == null || maxDailyLossRatio == null
                || cooldown == null || marketDataStaleAfter == null || tradingDayZone == null
                || feeBufferRatio == null || slippageBufferRatio == null) {
            throw new IllegalArgumentException("risk policy fields must not be null");
        }
        if (minOrderKrw.signum() < 0 || maxOrderKrw.signum() < 0 || maxExposureRatio.signum() < 0
                || maxDailyLossRatio.signum() < 0 || feeBufferRatio.signum() < 0 || slippageBufferRatio.signum() < 0) {
            throw new IllegalArgumentException("risk policy numeric values must be >= 0");
        }
        if (maxOrderKrw.compareTo(minOrderKrw) < 0) {
            throw new IllegalArgumentException("maxOrderKrw must be >= minOrderKrw");
        }
        if (cooldown.isNegative() || marketDataStaleAfter.isNegative()) {
            throw new IllegalArgumentException("policy durations must not be negative");
        }
    }

    /** Builds policy with defaults for stale-data window, timezone, and buffers. */
    public RiskPolicy(
            BigDecimal maxOrderKrw,
            BigDecimal maxExposureRatio,
            BigDecimal maxDailyLossRatio,
            Duration cooldown
    ) {
        this(
                BigDecimal.ZERO,
                maxOrderKrw,
                maxExposureRatio,
                maxDailyLossRatio,
                cooldown,
                Duration.ofSeconds(5),
                ZoneId.of("Asia/Seoul"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}
