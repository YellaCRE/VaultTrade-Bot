package com.vaulttradebot.domain.trading.snapshot;

import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import java.math.BigDecimal;
import java.time.Instant;

/** Snapshot for one active order. */
public record OpenOrderSnapshot(
        String orderId,
        Market market,
        Side side,
        Money price,
        BigDecimal quantity,
        String clientOrderId,
        Instant createdAt
) {
    public OpenOrderSnapshot {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        if (market == null || side == null || price == null || quantity == null || createdAt == null) {
            throw new IllegalArgumentException("open order fields must not be null");
        }
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
