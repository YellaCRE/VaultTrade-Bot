package com.vaulttradebot.application.port.out;

import com.vaulttradebot.application.outbox.OutboxMessage;
import java.time.Instant;
import java.util.List;

public interface OutboxRepository {
    void save(OutboxMessage message);

    List<OutboxMessage> findReadyToPublish(int limit, Instant now);

    void markPublished(String messageId, Instant publishedAt);

    void markFailed(String messageId, int attemptCount, Instant nextAttemptAt, String lastError);

    void markDeadLettered(String messageId, Instant deadLetteredAt, String lastError);

    List<OutboxMessage> findDeadLettered(int limit);

    void redriveDeadLetter(String messageId, Instant nextAttemptAt);

    List<OutboxMessage> findAll();
}
