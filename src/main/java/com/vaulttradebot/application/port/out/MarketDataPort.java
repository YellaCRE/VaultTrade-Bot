package com.vaulttradebot.application.port.out;

import com.vaulttradebot.domain.shared.Market;
import com.vaulttradebot.domain.shared.Money;

public interface MarketDataPort {
    Money getLastPrice(Market market);
}
