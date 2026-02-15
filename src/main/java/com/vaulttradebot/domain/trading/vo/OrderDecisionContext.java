package com.vaulttradebot.domain.trading.vo;

import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.trading.OrderMarketPolicy;
import com.vaulttradebot.domain.trading.snapshot.OpenOrderSnapshot;
import com.vaulttradebot.domain.trading.model.strategy.vo.SignalDecision;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/** Input snapshot for deterministic order decisions. */
public record OrderDecisionContext(
        SignalDecision signal,
        Market market,
        Money lastPrice,
        Money bestBidPrice,
        Money bestAskPrice,
        Instant marketDataAsOf,
        Instant now,
        BigDecimal maxOrderKrw,
        BigDecimal maxPositionQty,
        BigDecimal availableQuoteKrw,
        BigDecimal availableBaseQty,
        BigDecimal reservedQuoteKrw,
        BigDecimal reservedBaseQty,
        BigDecimal currentBaseQty,
        BigDecimal feeRatio,
        BigDecimal slippageBufferRatio,
        BigDecimal topBookQty,
        boolean riskAllowed,
        String riskReason,
        Optional<OpenOrderSnapshot> openOrder,
        String marketEventId,
        OrderMarketPolicy marketPolicy,
        Instant lastOrderAt
) {
    public OrderDecisionContext {
        if (signal == null || market == null || lastPrice == null || marketDataAsOf == null
                || now == null || maxOrderKrw == null || maxPositionQty == null || availableQuoteKrw == null
                || availableBaseQty == null || reservedQuoteKrw == null || reservedBaseQty == null
                || currentBaseQty == null || feeRatio == null || slippageBufferRatio == null
                || topBookQty == null || riskReason == null || riskReason.isBlank()
                || openOrder == null || marketPolicy == null) {
            throw new IllegalArgumentException("context fields must not be null or blank");
        }
        if (maxOrderKrw.signum() <= 0 || maxPositionQty.signum() < 0 || availableQuoteKrw.signum() < 0
                || availableBaseQty.signum() < 0 || reservedQuoteKrw.signum() < 0 || reservedBaseQty.signum() < 0
                || currentBaseQty.signum() < 0 || feeRatio.signum() < 0 || slippageBufferRatio.signum() < 0
                || topBookQty.signum() < 0) {
            throw new IllegalArgumentException("context numeric values must be valid");
        }
    }
}
