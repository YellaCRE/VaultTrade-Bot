package com.vaulttradebot.domain.trading;

import com.vaulttradebot.domain.shared.Market;
import com.vaulttradebot.domain.shared.Money;
import com.vaulttradebot.domain.shared.Side;
import java.math.BigDecimal;

public record OrderDecision(
        Market market,
        Side side,
        BigDecimal quantity,
        Money price,
        String reason
) {
    public OrderDecision {
        if (market == null || side == null || quantity == null || price == null) {
            throw new IllegalArgumentException("decision fields must not be null");
        }
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
