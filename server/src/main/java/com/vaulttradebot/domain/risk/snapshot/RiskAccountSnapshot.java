package com.vaulttradebot.domain.risk.snapshot;

import java.math.BigDecimal;

public record RiskAccountSnapshot(
        String accountId,
        BigDecimal referenceEquityKrw,
        BigDecimal availableCashKrw,
        BigDecimal reservedCashKrw,
        BigDecimal currentExposureKrw,
        BigDecimal realizedPnlKrw,
        BigDecimal unrealizedPnlKrw,
        BigDecimal dailyTradedKrw,
        java.time.Instant lastOrderAt
) {
    public RiskAccountSnapshot {
        if (accountId == null || accountId.isBlank() || referenceEquityKrw == null || availableCashKrw == null
                || reservedCashKrw == null || currentExposureKrw == null || realizedPnlKrw == null
                || unrealizedPnlKrw == null || dailyTradedKrw == null) {
            throw new IllegalArgumentException("account snapshot fields must not be null or blank");
        }
        if (referenceEquityKrw.signum() <= 0 || availableCashKrw.signum() < 0
                || reservedCashKrw.signum() < 0 || currentExposureKrw.signum() < 0 || dailyTradedKrw.signum() < 0) {
            throw new IllegalArgumentException("account snapshot numeric values out of range");
        }
    }

    /** Returns a new snapshot with updated reserved cash amount. */
    public RiskAccountSnapshot withReservedCashKrw(BigDecimal reservedCashKrw) {
        return new RiskAccountSnapshot(
                accountId,
                referenceEquityKrw,
                availableCashKrw,
                reservedCashKrw,
                currentExposureKrw,
                realizedPnlKrw,
                unrealizedPnlKrw,
                dailyTradedKrw,
                lastOrderAt
        );
    }

    /** Returns total PnL as realized plus unrealized PnL in KRW. */
    public BigDecimal totalPnlKrw() {
        return realizedPnlKrw.add(unrealizedPnlKrw);
    }

    /** Returns non-negative daily loss amount derived from total PnL. */
    public BigDecimal dailyLossKrw() {
        BigDecimal pnl = totalPnlKrw();
        return pnl.signum() < 0 ? pnl.negate() : BigDecimal.ZERO;
    }
}
