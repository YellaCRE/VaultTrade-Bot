package com.vaulttradebot.domain.trading.vo;

import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.trading.OrderMarketPolicy;
import com.vaulttradebot.domain.trading.snapshot.OpenOrderSnapshot;
import com.vaulttradebot.domain.trading.strategy.vo.SignalDecision;
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
        boolean riskAllowed,
        String riskReason,
        Optional<OpenOrderSnapshot> openOrder,
        String marketEventId,
        OrderMarketPolicy marketPolicy,
        Instant lastOrderAt
) {
    public OrderDecisionContext {
        if (signal == null || market == null || lastPrice == null || marketDataAsOf == null
                || now == null || maxOrderKrw == null || riskReason == null || riskReason.isBlank()
                || openOrder == null || marketPolicy == null) {
            throw new IllegalArgumentException("context fields must not be null or blank");
        }
        if (maxOrderKrw.signum() <= 0) {
            throw new IllegalArgumentException("maxOrderKrw must be positive");
        }
    }
}
