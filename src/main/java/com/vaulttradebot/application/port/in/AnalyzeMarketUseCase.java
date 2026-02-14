package com.vaulttradebot.application.port.in;

import com.vaulttradebot.application.query.MarketSnapshot;

public interface AnalyzeMarketUseCase {
    MarketSnapshot analyze(String symbol);
}
