package com.vaulttradebot.application.port.out;

import com.vaulttradebot.domain.shared.market.MarketSnapshot;

public interface LoadMarketSnapshotPort {
    MarketSnapshot loadSnapshot(String symbol);
}
