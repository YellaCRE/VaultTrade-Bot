package com.vaulttradebot.application.port.out;

import com.vaulttradebot.domain.common.vo.Candle;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Timeframe;
import java.time.Instant;
import java.util.List;

/** Provides normalized market prices/candles for application services. */
public interface MarketDataPort {
    Money getLastPrice(Market market);

    /** Returns candles aligned to timeframe and evaluation time for deterministic decisions. */
    List<Candle> getRecentCandles(Market market, Timeframe timeframe, int limit, Instant now);
}
