package com.vaulttradebot.application.port.out;

import com.vaulttradebot.application.outbox.OutboxMessage;

public interface OutboxEventPublisher {
    void publish(OutboxMessage message);
}
