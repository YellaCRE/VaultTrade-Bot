package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.port.out.MarketDataPort;
import com.vaulttradebot.domain.common.vo.Asset;
import com.vaulttradebot.domain.common.vo.Candle;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Price;
import com.vaulttradebot.domain.common.vo.Timeframe;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
/** In-memory deterministic market data source for local runs/tests. */
public class StaticMarketDataAdapter implements MarketDataPort {
    private static final Asset KRW = Asset.krw();

    private final Map<String, BigDecimal> prices = new ConcurrentHashMap<>();

    public StaticMarketDataAdapter() {
        prices.put("KRW-BTC", new BigDecimal("49000000"));
        prices.put("KRW-ETH", new BigDecimal("3100000"));
    }

    @Override
    public Money getLastPrice(Market market) {
        BigDecimal price = prices.getOrDefault(market.value(), new BigDecimal("1000"));
        return Money.of(price, KRW);
    }

    @Override
    public List<Candle> getRecentCandles(Market market, Timeframe timeframe, int limit, Instant now) {
        if (timeframe == null || limit <= 0 || now == null) {
            return Collections.emptyList();
        }
        // Build synthetic OHLCV data aligned to requested timeframe boundary.
        BigDecimal basePrice = prices.getOrDefault(market.value(), new BigDecimal("1000"));
        long frameSeconds = timeframe.duration().toSeconds();
        long alignedEpoch = (now.getEpochSecond() / frameSeconds) * frameSeconds;
        Instant alignedNow = Instant.ofEpochSecond(alignedEpoch);
        List<Candle> candles = new ArrayList<>(limit);
        for (int i = limit - 1; i >= 0; i--) {
            Instant openTime = alignedNow.minus(timeframe.duration().multipliedBy(i));
            BigDecimal drift = BigDecimal.valueOf((limit - i) % 7 - 3).multiply(new BigDecimal("1200"));
            BigDecimal open = basePrice.add(drift);
            BigDecimal close = open.add(BigDecimal.valueOf((limit - i) % 5 - 2).multiply(new BigDecimal("800")));
            BigDecimal high = open.max(close).add(new BigDecimal("500"));
            BigDecimal low = open.min(close).subtract(new BigDecimal("500"));
            candles.add(new Candle(
                    openTime,
                    Price.of(open, KRW),
                    Price.of(high, KRW),
                    Price.of(low.max(BigDecimal.ONE), KRW),
                    Price.of(close.max(BigDecimal.ONE), KRW),
                    BigDecimal.valueOf(10 + (limit - i))
            ));
        }
        return candles;
    }
}
