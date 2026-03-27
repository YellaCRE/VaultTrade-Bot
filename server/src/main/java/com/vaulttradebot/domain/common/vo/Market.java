package com.vaulttradebot.domain.common.vo;

import java.util.Locale;

public record Market(Asset quote, Asset base) {
    public Market {
        if (quote == null || base == null) {
            throw new IllegalArgumentException("market assets must not be null");
        }
        if (quote.equals(base)) {
            throw new IllegalArgumentException("quote and base must be different");
        }
    }

    /** Parses QUOTE-BASE text into a Market. */
    public static Market of(String code) {
        Asset[] parts = parse(code);
        return new Market(parts[0], parts[1]);
    }

    /** Returns canonical market code as QUOTE-BASE. */
    public String value() {
        return quote.code() + "-" + base.code();
    }

    private static Asset[] parse(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("market code must not be blank");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        String[] parts = normalized.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("market code must follow QUOTE-BASE format, e.g. KRW-BTC");
        }
        return new Asset[]{Asset.of(parts[0]), Asset.of(parts[1])};
    }
}
