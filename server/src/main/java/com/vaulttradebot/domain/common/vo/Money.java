package com.vaulttradebot.domain.common.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount, Asset currency) {
    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (currency == null) {
            throw new IllegalArgumentException("currency must not be null");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be >= 0");
        }
        if (!currency.isKrw()) {
            throw new IllegalArgumentException("money currency must be KRW");
        }
        amount = amount.setScale(0, RoundingMode.HALF_UP);
    }

    /** Generic money factory (currently KRW-only by invariant). */
    public static Money of(BigDecimal amount, Asset currency) {
        return new Money(amount, currency);
    }

    /** Convenience factory for KRW amount. */
    public static Money krw(BigDecimal amount) {
        return new Money(amount, Asset.krw());
    }

    /** Returns a new Money equal to this + other. */
    public Money add(Money other) {
        requireSameCurrency(other);
        return Money.of(amount.add(other.amount), currency);
    }

    /** Returns a new Money equal to this - other. */
    public Money subtract(Money other) {
        requireSameCurrency(other);
        return Money.of(amount.subtract(other.amount), currency);
    }

    /** Returns new Money scaled by multiplier. */
    public Money multiply(BigDecimal multiplier) {
        if (multiplier == null) {
            throw new IllegalArgumentException("multiplier must not be null");
        }
        return Money.of(amount.multiply(multiplier), currency);
    }

    /** Helper for callers that need sign checks. */
    public boolean isNegative() {
        return amount.signum() < 0;
    }

    private void requireSameCurrency(Money other) {
        if (other == null) {
            throw new IllegalArgumentException("other must not be null");
        }
        if (!Objects.equals(currency, other.currency)) {
            throw new IllegalArgumentException("currency mismatch");
        }
    }
}
