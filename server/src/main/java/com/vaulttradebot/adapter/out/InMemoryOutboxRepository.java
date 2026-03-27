package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.outbox.OutboxMessage;
import com.vaulttradebot.application.port.out.OutboxRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "vault.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryOutboxRepository implements OutboxRepository {
    private final List<OutboxMessage> messages = java.util.Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean failOnSave = new AtomicBoolean(false);

    @Override
    public void save(OutboxMessage message) {
        if (failOnSave.get()) {
            throw new IllegalStateException("forced outbox failure");
        }
        synchronized (messages) {
            boolean exists = messages.stream().anyMatch(current -> current.id().equals(message.id()));
            if (!exists) {
                messages.add(message);
            }
        }
    }

    @Override
    public List<OutboxMessage> findReadyToPublish(int limit, Instant now) {
        synchronized (messages) {
            return messages.stream()
                    .filter(message -> !message.isPublished())
                    .filter(message -> !message.isDeadLettered())
                    .filter(message -> message.nextAttemptAt() == null || !message.nextAttemptAt().isAfter(now))
                    .limit(Math.max(0, limit))
                    .toList();
        }
    }

    @Override
    public void markPublished(String messageId, Instant publishedAt) {
        updateMessage(messageId, current -> current.withPublishedAt(publishedAt));
    }

    @Override
    public void markFailed(String messageId, int attemptCount, Instant nextAttemptAt, String lastError) {
        updateMessage(messageId, current -> current.withFailure(attemptCount, lastError, nextAttemptAt));
    }

    @Override
    public void markDeadLettered(String messageId, Instant deadLetteredAt, String lastError) {
        updateMessage(messageId, current -> current.withDeadLetter(deadLetteredAt, lastError));
    }

    @Override
    public List<OutboxMessage> findDeadLettered(int limit) {
        synchronized (messages) {
            return messages.stream()
                    .filter(OutboxMessage::isDeadLettered)
                    .limit(Math.max(0, limit))
                    .toList();
        }
    }

    @Override
    public void redriveDeadLetter(String messageId, Instant nextAttemptAt) {
        updateMessage(messageId, current -> new OutboxMessage(
                current.id(),
                current.aggregateType(),
                current.aggregateId(),
                current.eventType(),
                current.payload(),
                current.payloadVersion(),
                current.occurredAt(),
                current.createdAt(),
                null,
                0,
                null,
                nextAttemptAt,
                null
        ));
    }

    @Override
    public List<OutboxMessage> findAll() {
        synchronized (messages) {
            return List.copyOf(messages);
        }
    }

    public void setFailOnSave(boolean shouldFail) {
        failOnSave.set(shouldFail);
    }

    List<OutboxMessage> snapshot() {
        synchronized (messages) {
            return new ArrayList<>(messages);
        }
    }

    void restore(List<OutboxMessage> snapshot) {
        synchronized (messages) {
            messages.clear();
            messages.addAll(snapshot);
        }
    }

    private void updateMessage(String messageId, java.util.function.Function<OutboxMessage, OutboxMessage> updater) {
        synchronized (messages) {
            for (int i = 0; i < messages.size(); i++) {
                OutboxMessage current = messages.get(i);
                if (current.id().equals(messageId)) {
                    messages.set(i, updater.apply(current));
                    break;
                }
            }
        }
    }
}
