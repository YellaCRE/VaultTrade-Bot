package com.vaulttradebot.application.port.in;

import com.vaulttradebot.domain.common.MarketSnapshot;

public interface AnalyzeMarketUseCase {
    MarketSnapshot analyze(String symbol);
}
