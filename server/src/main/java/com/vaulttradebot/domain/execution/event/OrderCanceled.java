package com.vaulttradebot.domain.execution.event;

import java.time.Instant;

public record OrderCanceled(
        String orderId,
        Instant occurredAt
) implements OrderDomainEvent {
}
