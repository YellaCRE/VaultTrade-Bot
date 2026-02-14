package com.vaulttradebot.domain.common.vo;

import java.util.Locale;
import java.util.Set;

public record AssetSymbol(String value) {
    private static final Set<String> FIAT_CODES = Set.of(
            "KRW", "USD", "JPY", "EUR", "GBP", "CNY", "HKD", "SGD"
    );

    public AssetSymbol {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("asset symbol must not be blank");
        }
        value = value.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9]{2,10}")) {
            throw new IllegalArgumentException("asset symbol format is invalid");
        }
    }

    public static AssetSymbol of(String value) {
        return new AssetSymbol(value);
    }

    public boolean isFiat() {
        return FIAT_CODES.contains(value);
    }

    public boolean isCrypto() {
        return !isFiat();
    }

    @Override
    public String toString() {
        return value;
    }
}
