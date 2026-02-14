package com.vaulttradebot.application.port.out;

import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;

public interface MarketDataPort {
    Money getLastPrice(Market market);
}
