package com.vaulttradebot.domain.trading.model.sizing.vo;

import java.math.BigDecimal;

/** Risk caps applied after strategy sizing. */
public record RiskCaps(
        BigDecimal maxOrderKrw,
        BigDecimal maxPositionQuantity,
        BigDecimal maxSlippageRatio,
        boolean allowStepUpForMinNotional
) {
    public RiskCaps {
        if (maxOrderKrw == null || maxPositionQuantity == null || maxSlippageRatio == null) {
            throw new IllegalArgumentException("risk caps must not be null");
        }
        if (maxOrderKrw.signum() < 0 || maxPositionQuantity.signum() < 0 || maxSlippageRatio.signum() < 0) {
            throw new IllegalArgumentException("risk caps must be >= 0");
        }
    }
}
