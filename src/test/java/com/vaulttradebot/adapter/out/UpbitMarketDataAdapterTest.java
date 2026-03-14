package com.vaulttradebot.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaulttradebot.adapter.out.upbit.UpbitQuotationClient;
import com.vaulttradebot.adapter.out.upbit.dto.UpbitMinuteCandleResponse;
import com.vaulttradebot.adapter.out.upbit.dto.UpbitTickerResponse;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Timeframe;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpbitMarketDataAdapterTest {
    private static final Market MARKET = Market.of("KRW-BTC");

    @Mock
    private UpbitQuotationClient quotationClient;

    @Test
    void mapsTickerTradePriceToKrwMoney() {
        when(quotationClient.getTicker("KRW-BTC"))
                .thenReturn(new UpbitTickerResponse("KRW-BTC", new BigDecimal("50234000")));

        UpbitMarketDataAdapter adapter = new UpbitMarketDataAdapter(quotationClient);
        Money lastPrice = adapter.getLastPrice(MARKET);

        assertThat(lastPrice).isEqualTo(Money.krw(new BigDecimal("50234000")));
    }

    @Test
    void mapsMinuteCandlesIntoAscendingDomainCandlesAndAlignsToFrame() {
        Instant now = Instant.parse("2026-03-14T10:37:21Z");
        when(quotationClient.getMinuteCandles(eq("KRW-BTC"), eq(60), eq(3), eq(Instant.parse("2026-03-14T10:00:00Z"))))
                .thenReturn(List.of(
                        candle("2026-03-14T09:00:00Z", "50000000", "50100000", "49900000", "50050000", "12.3", 60),
                        candle("2026-03-14T08:00:00Z", "49800000", "50000000", "49700000", "49900000", "10.5", 60),
                        candle("2026-03-14T07:00:00Z", "49500000", "49850000", "49450000", "49750000", "8.1", 60)
                ));

        UpbitMarketDataAdapter adapter = new UpbitMarketDataAdapter(quotationClient);
        var candles = adapter.getRecentCandles(MARKET, Timeframe.H1, 3, now);

        assertThat(candles).hasSize(3);
        assertThat(candles.get(0).openTime()).isEqualTo(Instant.parse("2026-03-14T07:00:00Z"));
        assertThat(candles.get(1).openTime()).isEqualTo(Instant.parse("2026-03-14T08:00:00Z"));
        assertThat(candles.get(2).openTime()).isEqualTo(Instant.parse("2026-03-14T09:00:00Z"));
        assertThat(candles.get(2).close().value()).isEqualByComparingTo("50050000.00000000");
        assertThat(candles.get(2).volume()).isEqualByComparingTo("12.3");
    }

    @Test
    void returnsEmptyListWhenQueryArgumentsAreInvalid() {
        UpbitMarketDataAdapter adapter = new UpbitMarketDataAdapter(quotationClient);

        assertThat(adapter.getRecentCandles(MARKET, Timeframe.M1, 0, Instant.now())).isEmpty();
        assertThat(adapter.getRecentCandles(MARKET, null, 10, Instant.now())).isEmpty();
        assertThat(adapter.getRecentCandles(MARKET, Timeframe.M1, 10, null)).isEmpty();
    }

    @Test
    void failsFastWhenTickerPriceIsMissing() {
        when(quotationClient.getTicker("KRW-BTC"))
                .thenReturn(new UpbitTickerResponse("KRW-BTC", null));

        UpbitMarketDataAdapter adapter = new UpbitMarketDataAdapter(quotationClient);

        assertThatThrownBy(() -> adapter.getLastPrice(MARKET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trade price was missing");
    }

    private UpbitMinuteCandleResponse candle(
            String openTimeUtc,
            String open,
            String high,
            String low,
            String close,
            String volume,
            int unit
    ) {
        return new UpbitMinuteCandleResponse(
                "KRW-BTC",
                OffsetDateTime.ofInstant(Instant.parse(openTimeUtc), ZoneOffset.UTC),
                new BigDecimal(open),
                new BigDecimal(high),
                new BigDecimal(low),
                new BigDecimal(close),
                new BigDecimal(volume),
                unit
        );
    }
}
