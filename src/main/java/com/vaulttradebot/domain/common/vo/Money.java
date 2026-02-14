package com.vaulttradebot.domain.common.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) {
    public static Money of(BigDecimal amount, Currency currency) {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (currency == null) {
            throw new IllegalArgumentException("currency must not be null");
        }
        BigDecimal normalized = amount.setScale(8, RoundingMode.HALF_UP);
        return new Money(normalized, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return Money.of(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return Money.of(amount.subtract(other.amount), currency);
    }

    public Money multiply(BigDecimal multiplier) {
        if (multiplier == null) {
            throw new IllegalArgumentException("multiplier must not be null");
        }
        return Money.of(amount.multiply(multiplier), currency);
    }

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
