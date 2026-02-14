package com.vaulttradebot.application.query;

import java.math.BigDecimal;

public record PortfolioSnapshot(
        String marketSymbol,
        BigDecimal quantity,
        BigDecimal averagePriceKrw,
        BigDecimal totalInvestmentKrw,
        BigDecimal marketValueKrw,
        BigDecimal realizedPnlKrw,
        BigDecimal unrealizedPnlKrw,
        BigDecimal totalPnlKrw
) {
}
