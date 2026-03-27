package com.vaulttradebot.domain.trading;

import java.math.BigDecimal;
import java.time.Duration;

/** Exchange constraints used by decision logic. */
public record OrderMarketPolicy(
        BigDecimal priceTick,
        BigDecimal quantityStep,
        BigDecimal minNotionalKrw,
        BigDecimal minQuantity,
        BigDecimal maxQuantity,
        BigDecimal maxSlippageRatio,
        BigDecimal depthFactor,
        boolean allowStepUpForMinNotional,
        BigDecimal maxSpreadRatio,
        BigDecimal replacePriceThresholdTicks,
        BigDecimal replaceQuantityThresholdSteps,
        Duration staleAfter,
        Duration cooldown
) {
    public OrderMarketPolicy {
        if (priceTick == null || quantityStep == null || minNotionalKrw == null || minQuantity == null
                || maxQuantity == null || maxSlippageRatio == null || depthFactor == null || maxSpreadRatio == null
                || replacePriceThresholdTicks == null || replaceQuantityThresholdSteps == null
                || staleAfter == null || cooldown == null) {
            throw new IllegalArgumentException("policy fields must not be null");
        }
        if (priceTick.signum() <= 0 || quantityStep.signum() <= 0 || minNotionalKrw.signum() < 0
                || minQuantity.signum() < 0 || maxQuantity.signum() <= 0 || maxSlippageRatio.signum() < 0
                || depthFactor.signum() < 0
                || maxSpreadRatio.signum() < 0 || replacePriceThresholdTicks.signum() < 0
                || replaceQuantityThresholdSteps.signum() < 0) {
            throw new IllegalArgumentException("invalid policy numeric values");
        }
        if (maxQuantity.compareTo(minQuantity) < 0) {
            throw new IllegalArgumentException("maxQuantity must be >= minQuantity");
        }
        if (staleAfter.isNegative() || cooldown.isNegative()) {
            throw new IllegalArgumentException("durations must not be negative");
        }
    }
}
