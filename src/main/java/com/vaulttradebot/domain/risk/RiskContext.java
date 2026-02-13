package com.vaulttradebot.domain.risk;

import java.math.BigDecimal;
import java.time.Instant;

public record RiskContext(
        BigDecimal requestedOrderKrw,
        BigDecimal currentExposureRatio,
        BigDecimal dailyLossRatio,
        Instant now,
        Instant lastOrderAt
) {
    public RiskContext {
        if (requestedOrderKrw == null || currentExposureRatio == null || dailyLossRatio == null || now == null) {
            throw new IllegalArgumentException("risk context fields must not be null");
        }
    }
}
