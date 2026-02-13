package com.vaulttradebot.interfaces.rest;

import com.vaulttradebot.domain.shared.Order;
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
