package com.vaulttradebot.application.port.out;

import com.vaulttradebot.application.query.MarketSnapshot;

public interface LoadMarketSnapshotPort {
    MarketSnapshot loadSnapshot(String symbol);
}
