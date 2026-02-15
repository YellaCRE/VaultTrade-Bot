package com.vaulttradebot.application.outbox;

import com.vaulttradebot.domain.execution.event.OrderDomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Durable outbox record written atomically with aggregate persistence. */
public record OutboxMessage(
        String id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        int payloadVersion,
        Instant occurredAt,
        Instant createdAt,
        Instant publishedAt,
        int attemptCount,
        String lastError,
        Instant nextAttemptAt,
        Instant deadLetteredAt
) {
    public OutboxMessage {
        if (id == null || id.isBlank() || aggregateType == null || aggregateType.isBlank()
                || aggregateId == null || aggregateId.isBlank() || eventType == null || eventType.isBlank()
                || payload == null || occurredAt == null || createdAt == null) {
            throw new IllegalArgumentException("outbox message fields must not be null or blank");
        }
        if (payloadVersion <= 0) {
            throw new IllegalArgumentException("payloadVersion must be positive");
        }
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must be >= 0");
        }
    }

    public static OutboxMessage fromOrderEvent(
            OrderDomainEvent event,
            String payload,
            int payloadVersion,
            Instant now
    ) {
        if (event == null || now == null) {
            throw new IllegalArgumentException("event, payload, and now must not be null");
        }
        return new OutboxMessage(
                UUID.randomUUID().toString(),
                "Order",
                event.orderId(),
                event.getClass().getSimpleName(),
                payload,
                payloadVersion,
                event.occurredAt(),
                now,
                null,
                0,
                null,
                now,
                null
        );
    }

    public OutboxMessage withPublishedAt(Instant publishedAt) {
        return new OutboxMessage(
                id,
                aggregateType,
                aggregateId,
                eventType,
                payload,
                payloadVersion,
                occurredAt,
                createdAt,
                publishedAt,
                attemptCount,
                lastError,
                nextAttemptAt,
                deadLetteredAt
        );
    }

    public OutboxMessage withFailure(int nextAttemptCount, String lastError, Instant nextAttemptAt) {
        return new OutboxMessage(
                id,
                aggregateType,
                aggregateId,
                eventType,
                payload,
                payloadVersion,
                occurredAt,
                createdAt,
                publishedAt,
                nextAttemptCount,
                lastError,
                nextAttemptAt,
                deadLetteredAt
        );
    }

    public OutboxMessage withDeadLetter(Instant deadLetteredAt, String lastError) {
        return new OutboxMessage(
                id,
                aggregateType,
                aggregateId,
                eventType,
                payload,
                payloadVersion,
                occurredAt,
                createdAt,
                publishedAt,
                attemptCount,
                lastError,
                null,
                deadLetteredAt
        );
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public boolean isDeadLettered() {
        return deadLetteredAt != null;
    }
}
