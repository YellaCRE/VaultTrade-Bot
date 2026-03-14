package com.vaulttradebot.adapter.out.upbit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record UpbitTickerResponse(
        @JsonProperty("market") String market,
        @JsonProperty("trade_price") BigDecimal tradePrice
) {
}
