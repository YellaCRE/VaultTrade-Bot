package com.vaulttradebot.domain.common.vo;

public record Market(String symbol) {
    public Market {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (!symbol.contains("-")) {
            throw new IllegalArgumentException("market symbol must follow QUOTE-BASE format, e.g. KRW-BTC");
        }
    }

    public String quoteAsset() {
        return symbol.split("-")[0];
    }

    public String baseAsset() {
        return symbol.split("-")[1];
    }
}
