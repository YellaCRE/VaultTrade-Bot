package com.vaulttradebot.domain.execution.event;

import java.time.Instant;

public record OrderPartiallyFilled(
        String orderId,
        Instant occurredAt
) implements OrderDomainEvent {
}
