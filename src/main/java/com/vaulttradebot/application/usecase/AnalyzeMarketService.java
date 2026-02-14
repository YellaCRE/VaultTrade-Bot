package com.vaulttradebot.application.usecase;

import com.vaulttradebot.application.port.in.AnalyzeMarketUseCase;
import com.vaulttradebot.application.port.out.LoadMarketSnapshotPort;
import com.vaulttradebot.application.query.MarketSnapshot;
import org.springframework.stereotype.Service;

@Service
public class AnalyzeMarketService implements AnalyzeMarketUseCase {
    private final LoadMarketSnapshotPort loadMarketSnapshotPort;

    public AnalyzeMarketService(LoadMarketSnapshotPort loadMarketSnapshotPort) {
        this.loadMarketSnapshotPort = loadMarketSnapshotPort;
    }

    @Override
    public MarketSnapshot analyze(String symbol) {
        return loadMarketSnapshotPort.loadSnapshot(symbol);
    }
}
