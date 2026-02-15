package com.vaulttradebot.application.port.out;

import com.vaulttradebot.domain.execution.event.OrderDomainEvent;

public interface OutboxPayloadSerializer {
    String serialize(OrderDomainEvent event);

    int payloadVersion();
}
