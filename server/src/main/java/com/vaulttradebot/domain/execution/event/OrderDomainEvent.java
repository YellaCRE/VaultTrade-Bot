package com.vaulttradebot.domain.execution.event;

import java.time.Instant;

public interface OrderDomainEvent {
    /** Returns the identifier of the order that produced this event. */
    String orderId();

    /** Returns the timestamp when this event occurred. */
    Instant occurredAt();
}
