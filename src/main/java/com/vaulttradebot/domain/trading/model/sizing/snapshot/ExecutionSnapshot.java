package com.vaulttradebot.domain.trading.model.sizing.snapshot;

import com.vaulttradebot.domain.common.vo.Money;
import java.math.BigDecimal;

/** Market and execution quality snapshot for quantity sizing. */
public record ExecutionSnapshot(
        Money lastPrice,
        Money bestBidPrice,
        Money bestAskPrice,
        BigDecimal slippageBufferRatio,
        BigDecimal feeRatio,
        BigDecimal topBookQuantity,
        BigDecimal depthFactor
) {
    public ExecutionSnapshot {
        if (lastPrice == null || slippageBufferRatio == null || feeRatio == null
                || topBookQuantity == null || depthFactor == null) {
            throw new IllegalArgumentException("execution snapshot fields must not be null");
        }
        if (slippageBufferRatio.signum() < 0 || feeRatio.signum() < 0
                || topBookQuantity.signum() < 0 || depthFactor.signum() < 0) {
            throw new IllegalArgumentException("execution snapshot numeric values must be >= 0");
        }
    }
}
