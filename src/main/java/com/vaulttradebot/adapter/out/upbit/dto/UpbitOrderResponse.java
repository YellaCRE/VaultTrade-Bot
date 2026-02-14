package com.vaulttradebot.adapter.out.upbit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpbitOrderResponse(
        String uuid,
        String state,
        String side,
        String market,
        String price,
        @JsonProperty("remaining_volume")
        String remainingVolume,
        @JsonProperty("executed_volume")
        String executedVolume
) {
}
