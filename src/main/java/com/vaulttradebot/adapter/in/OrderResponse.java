package com.vaulttradebot.adapter.in;

import com.vaulttradebot.config.ApiTimeSupport;
import com.vaulttradebot.domain.execution.Order;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderResponse(
        String id,
        String exchangeOrderId,
        String market,
        String side,
        BigDecimal quantity,
        BigDecimal priceKrw,
        String status,
        BigDecimal executedQuantity,
        BigDecimal executedAmountKrw,
        BigDecimal executedFeeKrw,
        OffsetDateTime createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.exchangeOrderId(),
                order.market().value(),
                order.side().name(),
                order.quantity(),
                order.price().amount(),
                order.status().name(),
                order.executedQuantity().value(),
                order.executedAmount().amount(),
                order.executedFee().amount(),
                ApiTimeSupport.toApiTime(order.createdAt())
        );
    }
}
