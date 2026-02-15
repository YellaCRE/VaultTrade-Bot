package com.vaulttradebot.domain.risk;

import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record RiskOrderRequest(
        String accountId,
        Market market,
        Side side,
        Money price,
        BigDecimal quantity,
        Instant requestedAt
) {
    public RiskOrderRequest {
        if (accountId == null || accountId.isBlank() || market == null || side == null
                || price == null || quantity == null || requestedAt == null) {
            throw new IllegalArgumentException("order request fields must not be null or blank");
        }
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    /** Calculates order notional in KRW using conservative ceiling rounding. */
    public BigDecimal orderNotionalKrw() {
        return price.amount().multiply(quantity).setScale(0, RoundingMode.CEILING);
    }
}
