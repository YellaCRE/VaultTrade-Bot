package com.vaulttradebot.domain.trading.sizing.vo;

import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.vo.OrderType;
import com.vaulttradebot.domain.trading.sizing.snapshot.AccountSnapshot;
import com.vaulttradebot.domain.trading.sizing.snapshot.ExecutionSnapshot;

import java.math.BigDecimal;

/** Full deterministic input for quantity calculation. */
public record QuantityCalculationRequest(
        Side side,
        OrderType orderType,
        double signalStrength,
        BigDecimal targetOrderKrw,
        Money limitPrice,
        ExchangeConstraints exchangeConstraints,
        RiskCaps riskCaps,
        ExecutionSnapshot executionSnapshot,
        AccountSnapshot accountSnapshot
) {
    public QuantityCalculationRequest {
        if (side == null || orderType == null || targetOrderKrw == null || limitPrice == null
                || exchangeConstraints == null || riskCaps == null || executionSnapshot == null
                || accountSnapshot == null) {
            throw new IllegalArgumentException("quantity request fields must not be null");
        }
        if (Double.isNaN(signalStrength) || Double.isInfinite(signalStrength) || signalStrength < 0.0d || signalStrength > 1.0d) {
            throw new IllegalArgumentException("signalStrength must be finite and in [0,1]");
        }
        if (targetOrderKrw.signum() < 0) {
            throw new IllegalArgumentException("targetOrderKrw must be >= 0");
        }
    }
}
