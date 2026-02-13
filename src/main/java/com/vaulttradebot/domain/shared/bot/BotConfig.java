package com.vaulttradebot.domain.shared.bot;

import java.math.BigDecimal;

public record BotConfig(
        String marketSymbol,
        boolean paperTrading,
        BigDecimal initialCashKrw,
        BigDecimal maxOrderKrw,
        BigDecimal maxExposureRatio,
        BigDecimal maxDailyLossRatio,
        long cooldownSeconds,
        BigDecimal buyThresholdPrice
) {
    public BotConfig {
        if (marketSymbol == null || marketSymbol.isBlank()) {
            throw new IllegalArgumentException("marketSymbol must not be blank");
        }
        if (initialCashKrw == null || maxOrderKrw == null || maxExposureRatio == null
                || maxDailyLossRatio == null || buyThresholdPrice == null) {
            throw new IllegalArgumentException("config numeric fields must not be null");
        }
        if (initialCashKrw.signum() <= 0 || maxOrderKrw.signum() <= 0 || buyThresholdPrice.signum() <= 0) {
            throw new IllegalArgumentException("cash/order/threshold must be positive");
        }
        if (maxExposureRatio.signum() < 0 || maxDailyLossRatio.signum() < 0) {
            throw new IllegalArgumentException("ratio values must be >= 0");
        }
        if (cooldownSeconds < 0) {
            throw new IllegalArgumentException("cooldownSeconds must be >= 0");
        }
    }

    public static BotConfig defaultConfig() {
        return new BotConfig(
                "KRW-BTC",
                true,
                new BigDecimal("1000000"),
                new BigDecimal("100000"),
                new BigDecimal("0.30"),
                new BigDecimal("0.03"),
                30L,
                new BigDecimal("50000000")
        );
    }
}
