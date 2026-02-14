package com.vaulttradebot.domain.execution.event;

import java.time.Instant;

public record OrderFilled(
        String orderId,
        Instant occurredAt
) implements OrderDomainEvent {
}
