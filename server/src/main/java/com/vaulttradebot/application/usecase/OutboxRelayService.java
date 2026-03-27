package com.vaulttradebot.application.usecase;

import com.vaulttradebot.application.outbox.OutboxMessage;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.port.out.OutboxEventPublisher;
import com.vaulttradebot.application.port.out.OutboxRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OutboxRelayService {
    private final OutboxRepository outboxRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final ClockPort clockPort;
    private final int maxAttempts;
    private final long retryBaseDelayMs;
    private final long retryMaxDelayMs;
    private final int defaultBatchSize;

    public OutboxRelayService(
            OutboxRepository outboxRepository,
            OutboxEventPublisher outboxEventPublisher,
            ClockPort clockPort,
            @Value("${vault.outbox.max-attempts:5}") int maxAttempts,
            @Value("${vault.outbox.retry-base-delay-ms:500}") long retryBaseDelayMs,
            @Value("${vault.outbox.retry-max-delay-ms:30000}") long retryMaxDelayMs,
            @Value("${vault.outbox.batch-size:100}") int defaultBatchSize
    ) {
        this.outboxRepository = outboxRepository;
        this.outboxEventPublisher = outboxEventPublisher;
        this.clockPort = clockPort;
        this.maxAttempts = maxAttempts;
        this.retryBaseDelayMs = retryBaseDelayMs;
        this.retryMaxDelayMs = retryMaxDelayMs;
        this.defaultBatchSize = defaultBatchSize;
    }

    @Scheduled(fixedDelayString = "${vault.outbox.relay-delay-ms:1000}")
    public void relay() {
        relayBatch(defaultBatchSize);
    }

    public int relayBatch(int limit) {
        Instant now = clockPort.now();
        List<OutboxMessage> pending = outboxRepository.findReadyToPublish(limit, now);
        int sent = 0;
        for (OutboxMessage message : pending) {
            try {
                outboxEventPublisher.publish(message);
                outboxRepository.markPublished(message.id(), now);
                sent++;
            } catch (RuntimeException publishError) {
                int nextAttemptCount = message.attemptCount() + 1;
                String error = safeError(publishError);
                if (nextAttemptCount >= maxAttempts) {
                    outboxRepository.markDeadLettered(message.id(), now, error);
                } else {
                    Instant nextAttemptAt = now.plusMillis(nextBackoffMillis(nextAttemptCount));
                    outboxRepository.markFailed(message.id(), nextAttemptCount, nextAttemptAt, error);
                }
            }
        }
        return sent;
    }

    private long nextBackoffMillis(int attemptCount) {
        long delay = retryBaseDelayMs;
        for (int i = 1; i < attemptCount; i++) {
            if (delay >= retryMaxDelayMs) {
                return retryMaxDelayMs;
            }
            delay = Math.min(retryMaxDelayMs, delay * 2);
        }
        return delay;
    }

    private String safeError(RuntimeException publishError) {
        String raw = publishError.getMessage();
        if (raw == null || raw.isBlank()) {
            return publishError.getClass().getSimpleName();
        }
        return raw.length() <= 500 ? raw : raw.substring(0, 500);
    }
}
