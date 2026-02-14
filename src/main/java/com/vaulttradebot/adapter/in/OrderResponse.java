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
        String state,
        Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.market().symbol(),
                order.side().name(),
                order.quantity(),
                order.price().amount(),
                order.state().name(),
                order.createdAt()
        );
    }
}
