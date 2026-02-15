package com.vaulttradebot.domain.trading.strategy;

/** Tunable parameters for MA-cross strategy behavior. */
public record StrategyConfig(
        String configId,
        int fastPeriod,
        int slowPeriod,
        int cooldownBars,
        boolean onlyOnChange
) {
    /** Validates MA strategy tuning parameters. */
    public StrategyConfig {
        if (configId == null || configId.isBlank()) {
            throw new IllegalArgumentException("configId must not be blank");
        }
        if (fastPeriod <= 0 || slowPeriod <= 0) {
            throw new IllegalArgumentException("period must be positive");
        }
        if (fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException("fastPeriod must be smaller than slowPeriod");
        }
        if (cooldownBars < 0) {
            throw new IllegalArgumentException("cooldownBars must be >= 0");
        }
    }
}
