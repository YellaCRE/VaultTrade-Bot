package com.vaulttradebot.adapter.out;

import com.vaulttradebot.adapter.out.upbit.UpbitQuotationClient;
import com.vaulttradebot.adapter.out.upbit.dto.UpbitMinuteCandleResponse;
import com.vaulttradebot.adapter.out.upbit.dto.UpbitTickerResponse;
import com.vaulttradebot.application.port.out.MarketDataPort;
import com.vaulttradebot.domain.common.vo.Asset;
import com.vaulttradebot.domain.common.vo.Candle;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Price;
import com.vaulttradebot.domain.common.vo.Timeframe;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
@ConditionalOnProperty(name = "vault.market-data.provider", havingValue = "upbit", matchIfMissing = true)
public class UpbitMarketDataAdapter implements MarketDataPort {
    private static final Asset KRW = Asset.krw();

    private final UpbitQuotationClient quotationClient;

    public UpbitMarketDataAdapter(UpbitQuotationClient quotationClient) {
        this.quotationClient = quotationClient;
    }

    @Override
    public Money getLastPrice(Market market) {
        UpbitTickerResponse ticker = quotationClient.getTicker(market.value());
        if (ticker.tradePrice() == null) {
            throw new IllegalStateException("upbit ticker trade price was missing for market " + market.value());
        }
        return Money.krw(ticker.tradePrice());
    }

    @Override
    public List<Candle> getRecentCandles(Market market, Timeframe timeframe, int limit, Instant now) {
        if (timeframe == null || limit <= 0 || now == null) {
            return List.of();
        }

        int unit = toUpbitMinuteUnit(timeframe);
        Instant alignedUpperBound = alignToFrame(now, timeframe);

        return quotationClient.getMinuteCandles(market.value(), unit, limit, alignedUpperBound).stream()
                .filter(this::hasRequiredFields)
                .sorted(Comparator.comparing(response -> response.candleDateTimeUtc().toInstant()))
                .map(this::toDomainCandle)
                .toList();
    }

    private int toUpbitMinuteUnit(Timeframe timeframe) {
        return switch (timeframe) {
            case M1 -> 1;
            case M5 -> 5;
            case M15 -> 15;
            case H1 -> 60;
        };
    }

    private Instant alignToFrame(Instant now, Timeframe timeframe) {
        long frameSeconds = timeframe.duration().toSeconds();
        long alignedEpoch = (now.getEpochSecond() / frameSeconds) * frameSeconds;
        return Instant.ofEpochSecond(alignedEpoch);
    }

    private boolean hasRequiredFields(UpbitMinuteCandleResponse response) {
        return response != null
                && response.candleDateTimeUtc() != null
                && response.openingPrice() != null
                && response.highPrice() != null
                && response.lowPrice() != null
                && response.tradePrice() != null
                && response.candleAccTradeVolume() != null;
    }

    private Candle toDomainCandle(UpbitMinuteCandleResponse response) {
        BigDecimal low = response.lowPrice().max(BigDecimal.ZERO);
        BigDecimal close = response.tradePrice().max(BigDecimal.ZERO);
        return new Candle(
                response.candleDateTimeUtc().toInstant(),
                Price.of(response.openingPrice(), KRW),
                Price.of(response.highPrice(), KRW),
                Price.of(low, KRW),
                Price.of(close, KRW),
                response.candleAccTradeVolume()
        );
    }
}
