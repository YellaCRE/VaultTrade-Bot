package com.vaulttradebot.adapter.out.upbit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record UpbitMinuteCandleResponse(
        @JsonProperty("market") String market,
        @JsonProperty("candle_date_time_utc") OffsetDateTime candleDateTimeUtc,
        @JsonProperty("opening_price") BigDecimal openingPrice,
        @JsonProperty("high_price") BigDecimal highPrice,
        @JsonProperty("low_price") BigDecimal lowPrice,
        @JsonProperty("trade_price") BigDecimal tradePrice,
        @JsonProperty("candle_acc_trade_volume") BigDecimal candleAccTradeVolume,
        @JsonProperty("unit") Integer unit
) {
}
