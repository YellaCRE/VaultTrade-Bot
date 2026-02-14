package com.vaulttradebot.adapter.in;

import com.vaulttradebot.domain.execution.Order;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        String id,
        String market,
        String side,
        BigDecimal quantity,
        BigDecimal priceKrw,
        String status,
        BigDecimal executedQuantity,
        BigDecimal executedAmountKrw,
        Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.market().value(),
                order.side().name(),
                order.quantity(),
                order.price().amount(),
                order.status().name(),
                order.executedQuantity().value(),
                order.executedAmount().amount(),
                order.createdAt()
        );
    }
}
