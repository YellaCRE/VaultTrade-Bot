package com.vaulttradebot.domain.trading.model.strategy.vo;

import com.vaulttradebot.domain.common.vo.Timeframe;

/** State bucket key per symbol and timeframe. */
public record StrategyKey(String symbol, Timeframe timeframe) {
    /** Validates the state partition key fields. */
    public StrategyKey {
        if (symbol == null || symbol.isBlank() || timeframe == null) {
            throw new IllegalArgumentException("strategy key fields must not be null or blank");
        }
    }
}
