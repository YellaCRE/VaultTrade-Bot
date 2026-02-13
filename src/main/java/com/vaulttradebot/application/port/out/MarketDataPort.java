package com.vaulttradebot.application.port.out;

import com.vaulttradebot.domain.shared.market.Market;
import com.vaulttradebot.domain.shared.market.Money;

public interface MarketDataPort {
    Money getLastPrice(Market market);
}
