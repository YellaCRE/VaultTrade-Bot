package com.vaulttradebot.adapter.out.upbit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpbitCreateOrderRequest(
        String market,
        String side,
        String volume,
        String price,
        @JsonProperty("ord_type")
        String ordType,
        String identifier
) {
}
