package com.vaulttradebot.domain.trading.model.strategy.vo;

import com.vaulttradebot.domain.trading.vo.SignalAction;
import com.vaulttradebot.domain.common.vo.Timeframe;

import java.time.Instant;

/** Standard strategy output consumed by order/risk layers. */
public record SignalDecision(
        SignalAction action,
        double confidence,
        String reason,
        Instant signalAt,
        String symbol,
        Timeframe timeframe
) {
    /** Validates normalized signal output fields. */
    public SignalDecision {
        if (action == null || reason == null || reason.isBlank() || signalAt == null
                || symbol == null || symbol.isBlank() || timeframe == null) {
            throw new IllegalArgumentException("signal decision fields must not be null or blank");
        }
        if (confidence < 0.0d || confidence > 1.0d || Double.isNaN(confidence) || Double.isInfinite(confidence)) {
            throw new IllegalArgumentException("confidence must be finite and in range [0,1]");
        }
    }

    /** Creates a HOLD decision with standardized zero confidence. */
    public static SignalDecision hold(
            String reason,
            Instant signalAt,
            String symbol,
            Timeframe timeframe
    ) {
        // Helper for explicit "no trade" outcomes with traceable reason.
        return new SignalDecision(SignalAction.HOLD, 0.0d, reason, signalAt, symbol, timeframe);
    }
}
