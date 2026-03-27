package com.vaulttradebot.domain.portfolio.event;

import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;

import java.time.Instant;

public record FeeCharged(
        Market market,
        Money fee,
        Instant occurredAt
) implements PositionEvent {
    /** Validates fee event payload for position adjustment. */
    public FeeCharged {
        if (market == null || fee == null || occurredAt == null) {
            throw new IllegalArgumentException("fee charged fields must not be null");
        }
    }
}
