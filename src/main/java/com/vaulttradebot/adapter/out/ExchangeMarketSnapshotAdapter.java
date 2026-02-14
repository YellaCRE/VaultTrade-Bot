package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.port.out.LoadMarketSnapshotPort;
import com.vaulttradebot.application.query.MarketSnapshot;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class ExchangeMarketSnapshotAdapter implements LoadMarketSnapshotPort {
    @Override
    public MarketSnapshot loadSnapshot(String symbol) {
        return new MarketSnapshot(symbol, BigDecimal.ZERO);
    }
}
