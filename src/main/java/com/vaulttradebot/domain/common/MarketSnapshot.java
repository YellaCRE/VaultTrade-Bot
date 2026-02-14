package com.vaulttradebot.domain.common;

import java.math.BigDecimal;

public record MarketSnapshot(String symbol, BigDecimal lastPrice) {
}
