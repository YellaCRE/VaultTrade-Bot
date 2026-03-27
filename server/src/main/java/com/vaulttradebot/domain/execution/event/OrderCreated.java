package com.vaulttradebot.domain.execution.event;

import java.time.Instant;

public record OrderCreated(
        String orderId,
        Instant occurredAt
) implements OrderDomainEvent {
}
