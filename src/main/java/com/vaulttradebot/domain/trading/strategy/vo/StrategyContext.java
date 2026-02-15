package com.vaulttradebot.domain.trading.strategy.vo;

import com.vaulttradebot.domain.common.vo.Candle;
import com.vaulttradebot.domain.common.vo.Timeframe;
import com.vaulttradebot.domain.trading.strategy.snapshot.StrategyPositionSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Immutable input contract for one strategy evaluation step. */
public record StrategyContext(
        String symbol,
        List<Candle> marketDataWindow,
        Timeframe timeframe,
        Instant now,
        Optional<StrategyPositionSnapshot> positionSnapshot
) {
    /** Validates the strategy input contract before evaluation. */
    public StrategyContext {
        if (symbol == null || symbol.isBlank() || marketDataWindow == null
                || timeframe == null || now == null || positionSnapshot == null) {
            throw new IllegalArgumentException("strategy context fields must not be null or blank");
        }
    }
}
