package com.vaulttradebot.application.port.out;

import com.vaulttradebot.domain.shared.MarketSnapshot;

public interface LoadMarketSnapshotPort {
    MarketSnapshot loadSnapshot(String symbol);
}
