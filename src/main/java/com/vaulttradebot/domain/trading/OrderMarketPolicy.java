package com.vaulttradebot.domain.trading;

import java.math.BigDecimal;
import java.time.Duration;

/** Exchange constraints used by decision logic. */
public record OrderMarketPolicy(
        BigDecimal priceTick,
        BigDecimal quantityStep,
        BigDecimal minNotionalKrw,
        BigDecimal maxSpreadRatio,
        BigDecimal replacePriceThresholdTicks,
        BigDecimal replaceQuantityThresholdSteps,
        Duration staleAfter,
        Duration cooldown
) {
    public OrderMarketPolicy {
        if (priceTick == null || quantityStep == null || minNotionalKrw == null || maxSpreadRatio == null
                || replacePriceThresholdTicks == null || replaceQuantityThresholdSteps == null
                || staleAfter == null || cooldown == null) {
            throw new IllegalArgumentException("policy fields must not be null");
        }
        if (priceTick.signum() <= 0 || quantityStep.signum() <= 0 || minNotionalKrw.signum() < 0
                || maxSpreadRatio.signum() < 0 || replacePriceThresholdTicks.signum() < 0
                || replaceQuantityThresholdSteps.signum() < 0) {
            throw new IllegalArgumentException("invalid policy numeric values");
        }
        if (staleAfter.isNegative() || cooldown.isNegative()) {
            throw new IllegalArgumentException("durations must not be negative");
        }
    }
}
