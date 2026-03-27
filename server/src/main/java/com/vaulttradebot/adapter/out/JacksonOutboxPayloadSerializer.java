package com.vaulttradebot.adapter.out;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaulttradebot.application.port.out.OutboxPayloadSerializer;
import com.vaulttradebot.domain.execution.event.OrderDomainEvent;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JacksonOutboxPayloadSerializer implements OutboxPayloadSerializer {
    private static final int VERSION = 1;
    private final ObjectMapper objectMapper;

    public JacksonOutboxPayloadSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String serialize(OrderDomainEvent event) {
        try {
            Map<String, Object> envelope = Map.of(
                    "schemaVersion", VERSION,
                    "eventType", event.getClass().getSimpleName(),
                    "occurredAt", event.occurredAt().toString(),
                    "data", event
            );
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize outbox event", e);
        }
    }

    @Override
    public int payloadVersion() {
        return VERSION;
    }
}
