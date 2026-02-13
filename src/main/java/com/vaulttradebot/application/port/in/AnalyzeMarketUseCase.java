package com.vaulttradebot.application.port.in;

import com.vaulttradebot.domain.shared.MarketSnapshot;

public interface AnalyzeMarketUseCase {
    MarketSnapshot analyze(String symbol);
}
