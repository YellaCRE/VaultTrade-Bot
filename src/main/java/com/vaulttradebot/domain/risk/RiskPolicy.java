package com.vaulttradebot.domain.risk;

import java.math.BigDecimal;
import java.time.Duration;

public record RiskPolicy(
        BigDecimal maxOrderKrw,
        BigDecimal maxExposureRatio,
        BigDecimal maxDailyLossRatio,
        Duration cooldown
) {
    public RiskPolicy {
        if (maxOrderKrw == null || maxExposureRatio == null || maxDailyLossRatio == null || cooldown == null) {
            throw new IllegalArgumentException("risk policy fields must not be null");
        }
        if (maxOrderKrw.signum() < 0 || maxExposureRatio.signum() < 0 || maxDailyLossRatio.signum() < 0) {
            throw new IllegalArgumentException("risk policy numeric values must be >= 0");
        }
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown must not be negative");
        }
    }
}
