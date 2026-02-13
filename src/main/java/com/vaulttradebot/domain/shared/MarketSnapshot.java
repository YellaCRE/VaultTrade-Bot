package com.vaulttradebot.domain.shared;

import java.math.BigDecimal;

public record MarketSnapshot(String symbol, BigDecimal lastPrice) {
}
