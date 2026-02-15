package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.outbox.OutboxMessage;
import com.vaulttradebot.application.port.out.OutboxEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingOutboxEventPublisher implements OutboxEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoggingOutboxEventPublisher.class);

    @Override
    public void publish(OutboxMessage message) {
        log.info(
                "outbox_publish messageId={} aggregateType={} aggregateId={} eventType={} payload={}",
                message.id(),
                message.aggregateType(),
                message.aggregateId(),
                message.eventType(),
                message.payload()
        );
    }
}
