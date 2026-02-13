package com.vaulttradebot.domain.shared;

public record Market(String symbol, String baseAsset, String quoteAsset) {
    public Market {
        if (isBlank(symbol) || isBlank(baseAsset) || isBlank(quoteAsset)) {
            throw new IllegalArgumentException("market fields must not be blank");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
