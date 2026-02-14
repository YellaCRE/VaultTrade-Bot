package com.vaulttradebot.domain.common.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount, AssetSymbol currency) {
    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (currency == null) {
            throw new IllegalArgumentException("currency must not be null");
        }
        if (!currency.isFiat()) {
            throw new IllegalArgumentException("money supports fiat currency only");
        }
        amount = amount.setScale(scaleFor(currency), RoundingMode.HALF_UP);
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("money amount must be >= 0");
        }
    }

    public static Money of(BigDecimal amount, AssetSymbol currency) {
        return new Money(amount, currency);
    }

    public static Money krw(BigDecimal amount) {
        return new Money(amount, AssetSymbol.of("KRW"));
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        BigDecimal result = amount.subtract(other.amount);
        if (result.signum() < 0) {
            throw new IllegalArgumentException("money amount cannot be negative");
        }
        return new Money(result, currency);
    }

    public Money multiply(BigDecimal factor) {
        if (factor == null) {
            throw new IllegalArgumentException("factor must not be null");
        }
        if (factor.signum() < 0) {
            throw new IllegalArgumentException("factor must be >= 0");
        }
        return new Money(amount.multiply(factor), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    private void requireSameCurrency(Money other) {
        if (other == null) {
            throw new IllegalArgumentException("money must not be null");
        }
        if (!Objects.equals(currency, other.currency)) {
            throw new IllegalArgumentException("currency mismatch");
        }
    }

    private static int scaleFor(AssetSymbol currency) {
        return switch (currency.value()) {
            case "KRW", "JPY" -> 0;
            default -> 2;
        };
    }
}
