package com.vaulttradebot.domain.common.vo;

public record Market(AssetSymbol quote, AssetSymbol base) {
    public Market {
        if (quote == null || base == null) {
            throw new IllegalArgumentException("market symbols must not be null");
        }
        if (quote.equals(base)) {
            throw new IllegalArgumentException("quote and base must be different");
        }
    }

    public static Market of(AssetSymbol quote, AssetSymbol base) {
        return new Market(quote, base);
    }

    public static Market parse(String marketCode) {
        if (marketCode == null || marketCode.isBlank()) {
            throw new IllegalArgumentException("market code must not be blank");
        }
        String[] parts = marketCode.trim().toUpperCase().split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("market code must follow QUOTE-BASE format");
        }
        return new Market(AssetSymbol.of(parts[0]), AssetSymbol.of(parts[1]));
    }

    public boolean isQuoteFiat() {
        return quote.isFiat();
    }

    public String symbol() {
        return quote + "-" + base;
    }

    @Override
    public String toString() {
        return symbol();
    }
}
