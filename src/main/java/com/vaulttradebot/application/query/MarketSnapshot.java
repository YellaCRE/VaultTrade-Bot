package com.vaulttradebot.application.query;

import java.math.BigDecimal;

public record MarketSnapshot(
        String symbol,
        BigDecimal lastPrice
) {
}
