package com.vaulttradebot.domain.common.vo;

import java.util.Locale;
import java.util.regex.Pattern;

public record Asset(String code) {
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9]{2,10}");
    private static final Asset KRW = new Asset("KRW");

    public Asset {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("asset code must not be blank");
        }
        code = code.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("asset code must match [A-Z0-9]{2,10}");
        }
    }

    /** Creates an Asset from a raw code (normalized to uppercase). */
    public static Asset of(String code) {
        return new Asset(code);
    }

    /** Returns the shared KRW asset instance. */
    public static Asset krw() {
        return KRW;
    }

    /** Returns true when this asset is KRW. */
    public boolean isKrw() {
        return "KRW".equals(code);
    }
}
