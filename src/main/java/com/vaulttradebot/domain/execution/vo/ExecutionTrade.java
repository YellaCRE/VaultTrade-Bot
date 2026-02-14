package com.vaulttradebot.domain.execution.vo;

import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Quantity;
import java.math.RoundingMode;
import java.time.Instant;

public record ExecutionTrade(
        String tradeId,
        Money price,
        Quantity quantity,
        Instant executedAt
) {
    public ExecutionTrade {
        if (tradeId == null || tradeId.isBlank()) {
            throw new IllegalArgumentException("tradeId must not be blank");
        }
        if (price == null || quantity == null || executedAt == null) {
            throw new IllegalArgumentException("trade fields must not be null");
        }
        if (quantity.value().signum() == 0) {
            throw new IllegalArgumentException("trade quantity must be positive");
        }
    }

    /** Calculates the executed KRW amount for this fill trade. */
    public Money executedAmount() {
        return Money.of(
                price.amount().multiply(quantity.value()).setScale(0, RoundingMode.DOWN),
                price.currency()
        );
    }
}
