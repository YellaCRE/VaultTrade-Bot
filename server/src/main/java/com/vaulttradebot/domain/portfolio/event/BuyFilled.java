package com.vaulttradebot.domain.portfolio.event;

import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Quantity;

import java.math.BigDecimal;
import java.time.Instant;

public record BuyFilled(
        Market market,
        Quantity quantity,
        Money price,
        Money fee,
        Instant occurredAt
) implements PositionEvent {
    /** Normalizes and validates buy fill event payload. */
    public BuyFilled {
        if (market == null || quantity == null || price == null || occurredAt == null) {
            throw new IllegalArgumentException("buy fill fields must not be null");
        }
        if (quantity.value().signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (fee == null) {
            fee = Money.krw(BigDecimal.ZERO);
        }
    }
}
