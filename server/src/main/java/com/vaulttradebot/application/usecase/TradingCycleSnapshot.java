package com.vaulttradebot.application.usecase;

import com.vaulttradebot.domain.trading.vo.OrderDecisionType;
import java.math.BigDecimal;
import java.time.Instant;

public record TradingCycleSnapshot(
        String cycleId,
        String strategyId,
        String pair,
        String timeframe,
        Instant dataTimestamp,
        BigDecimal lastPrice,
        BigDecimal availableQuoteKrw,
        BigDecimal positionQuantity,
        String signalAction,
        String signalReason,
        boolean riskAllowed,
        String riskReasonCode,
        OrderDecisionType decisionType,
        String decisionReason,
        String commandType,
        String commandId,
        String outboxEventId,
        long latencyMs,
        String errorReason,
        Instant createdAt
) {
    public TradingCycleSnapshot {
        if (cycleId == null || cycleId.isBlank()
                || strategyId == null || strategyId.isBlank()
                || pair == null || pair.isBlank()
                || timeframe == null || timeframe.isBlank()
                || dataTimestamp == null
                || signalAction == null || signalAction.isBlank()
                || signalReason == null || signalReason.isBlank()
                || decisionType == null
                || decisionReason == null || decisionReason.isBlank()
                || createdAt == null) {
            throw new IllegalArgumentException("trading cycle snapshot required fields are missing");
        }
        if (lastPrice != null && lastPrice.signum() < 0) {
            throw new IllegalArgumentException("lastPrice must be >= 0");
        }
        if (availableQuoteKrw != null && availableQuoteKrw.signum() < 0) {
            throw new IllegalArgumentException("availableQuoteKrw must be >= 0");
        }
        if (positionQuantity != null && positionQuantity.signum() < 0) {
            throw new IllegalArgumentException("positionQuantity must be >= 0");
        }
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must be >= 0");
        }
    }

    public CycleResult toCycleResult() {
        boolean orderRequested = decisionType != OrderDecisionType.HOLD;
        String message = "cycle replayed: " + decisionType + " (" + decisionReason + ")";
        return new CycleResult(true, orderRequested, message);
    }
}
