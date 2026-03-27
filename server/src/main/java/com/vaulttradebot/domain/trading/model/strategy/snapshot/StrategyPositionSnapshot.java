package com.vaulttradebot.domain.trading.model.strategy.snapshot;

import com.vaulttradebot.domain.common.vo.Side;
import java.math.BigDecimal;

/** Optional lightweight position view for strategy hints. */
public record StrategyPositionSnapshot(
        Side side,
        BigDecimal quantity
) {
    /** Validates optional position hints used by strategy logic. */
    public StrategyPositionSnapshot {
        if (side == null || quantity == null) {
            throw new IllegalArgumentException("position snapshot fields must not be null");
        }
        if (quantity.signum() < 0) {
            throw new IllegalArgumentException("quantity must be >= 0");
        }
    }
}
