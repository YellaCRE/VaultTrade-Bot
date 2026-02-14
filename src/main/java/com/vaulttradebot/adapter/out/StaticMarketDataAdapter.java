package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.port.out.MarketDataPort;
import com.vaulttradebot.domain.common.vo.AssetSymbol;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class StaticMarketDataAdapter implements MarketDataPort {
    private static final AssetSymbol KRW = AssetSymbol.of("KRW");

    private final Map<String, BigDecimal> prices = new ConcurrentHashMap<>();

    public StaticMarketDataAdapter() {
        prices.put("KRW-BTC", new BigDecimal("49000000"));
        prices.put("KRW-ETH", new BigDecimal("3100000"));
    }

    @Override
    public Money getLastPrice(Market market) {
        BigDecimal price = prices.getOrDefault(market.symbol(), new BigDecimal("1000"));
        return Money.of(price, KRW);
    }
}
