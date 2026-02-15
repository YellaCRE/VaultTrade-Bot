package com.vaulttradebot.domain.trading.sizing.vo;

import java.math.BigDecimal;

/** Result for quantity sizing; holdReason is set when order should be skipped. */
public record QuantityCalculationResult(
        BigDecimal quantity,
        String holdReason
) {
    public static QuantityCalculationResult hold(String reason) {
        return new QuantityCalculationResult(BigDecimal.ZERO, reason);
    }

    public static QuantityCalculationResult tradable(BigDecimal quantity) {
        return new QuantityCalculationResult(quantity, null);
    }

    public boolean isTradable() {
        return holdReason == null && quantity != null && quantity.signum() > 0;
    }
}
