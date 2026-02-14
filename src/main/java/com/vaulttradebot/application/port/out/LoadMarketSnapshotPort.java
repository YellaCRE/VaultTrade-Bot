package com.vaulttradebot.application.port.out;

import com.vaulttradebot.domain.common.MarketSnapshot;

public interface LoadMarketSnapshotPort {
    MarketSnapshot loadSnapshot(String symbol);
}
