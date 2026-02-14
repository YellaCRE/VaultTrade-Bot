package com.vaulttradebot.domain.portfolio.event;

import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Price;
import com.vaulttradebot.domain.common.vo.Quantity;

import java.time.Instant;

public record BalanceAdjusted(
        Market market,
        Quantity quantity,
        Price avgPrice,
        Instant occurredAt
) implements PositionEvent {
    /** Validates exchange sync payload used for balance correction. */
    public BalanceAdjusted {
        if (market == null || quantity == null || avgPrice == null || occurredAt == null) {
            throw new IllegalArgumentException("balance adjusted fields must not be null");
        }
        if (quantity.value().signum() == 0 && avgPrice.value().signum() != 0) {
            throw new IllegalArgumentException("avgPrice must be 0 when quantity is 0");
        }
    }
}
