package com.vaulttradebot.domain.risk.vo;

import com.vaulttradebot.domain.risk.RiskPolicy;
import com.vaulttradebot.domain.risk.event.RiskOrderRequest;
import com.vaulttradebot.domain.risk.snapshot.RiskAccountSnapshot;
import com.vaulttradebot.domain.risk.snapshot.RiskMarketSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;

public record RiskContext(
        RiskOrderRequest orderRequest,
        RiskAccountSnapshot accountSnapshot,
        RiskMarketSnapshot marketSnapshot,
        RiskPolicy policy,
        Clock clock
) {
    public RiskContext {
        if (orderRequest == null || accountSnapshot == null || marketSnapshot == null || policy == null || clock == null) {
            throw new IllegalArgumentException("risk context fields must not be null");
        }
        if (!orderRequest.accountId().equals(accountSnapshot.accountId())) {
            throw new IllegalArgumentException("account id mismatch between request and account snapshot");
        }
        if (!orderRequest.market().value().equals(marketSnapshot.marketSymbol())) {
            throw new IllegalArgumentException("market mismatch between request and market snapshot");
        }
    }

    /** Returns the current evaluation time from the injected clock. */
    public Instant now() {
        return Instant.now(clock);
    }

    /** Returns raw order notional before any safety buffers are applied. */
    public BigDecimal requestedOrderKrwRaw() {
        return orderRequest.orderNotionalKrw();
    }

    /** Returns conservative order notional including fee and slippage buffers. */
    public BigDecimal requestedOrderKrwConservative() {
        BigDecimal multiplier = BigDecimal.ONE
                .add(policy.feeBufferRatio())
                .add(policy.slippageBufferRatio());
        return requestedOrderKrwRaw()
                .multiply(multiplier)
                .setScale(0, RoundingMode.CEILING);
    }

    /** Clones the context with updated reserved cash for reservation-aware checks. */
    public RiskContext withReservedCash(BigDecimal reservedCashKrw) {
        return new RiskContext(
                orderRequest,
                accountSnapshot.withReservedCashKrw(reservedCashKrw),
                marketSnapshot,
                policy,
                clock
        );
    }
}
