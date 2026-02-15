package com.vaulttradebot.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaulttradebot.adapter.out.InMemoryOutboxRepository;
import com.vaulttradebot.application.outbox.OutboxMessage;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.port.out.OutboxEventPublisher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutboxRelayServiceTest {

    @Test
    void publishesReadyMessagesAndMarksThemAsPublished() {
        // Verifies relay publishes pending outbox messages and updates published timestamp.
        InMemoryOutboxRepository outboxRepository = new InMemoryOutboxRepository();
        Instant now = Instant.parse("2026-02-15T10:00:00Z");
        ClockPort clock = () -> now;
        RecordingPublisher publisher = new RecordingPublisher();
        OutboxRelayService relayService = new OutboxRelayService(outboxRepository, publisher, clock, 5, 500, 30000, 100);

        outboxRepository.save(message("msg-1", now));

        int published = relayService.relayBatch(10);

        assertThat(published).isEqualTo(1);
        assertThat(publisher.published()).hasSize(1);
        assertThat(outboxRepository.findAll().getFirst().isPublished()).isTrue();
    }

    @Test
    void respectsBatchLimit() {
        // Verifies relay sends at most the configured batch size.
        InMemoryOutboxRepository outboxRepository = new InMemoryOutboxRepository();
        Instant now = Instant.parse("2026-02-15T10:00:00Z");
        ClockPort clock = () -> now;
        RecordingPublisher publisher = new RecordingPublisher();
        OutboxRelayService relayService = new OutboxRelayService(outboxRepository, publisher, clock, 5, 500, 30000, 100);

        outboxRepository.save(message("msg-1", now));
        outboxRepository.save(message("msg-2", now));

        int published = relayService.relayBatch(1);

        assertThat(published).isEqualTo(1);
        assertThat(publisher.published()).hasSize(1);
        assertThat(outboxRepository.findReadyToPublish(10, now)).hasSize(1);
    }

    @Test
    void schedulesRetryOnPublishFailure() {
        // Verifies publish failure increments attempt count and schedules next retry.
        InMemoryOutboxRepository outboxRepository = new InMemoryOutboxRepository();
        Instant now = Instant.parse("2026-02-15T10:00:00Z");
        ClockPort clock = () -> now;
        OutboxRelayService relayService = new OutboxRelayService(
                outboxRepository,
                message -> {
                    throw new IllegalStateException("temporary network");
                },
                clock,
                3,
                1000,
                8000,
                100
        );

        outboxRepository.save(message("msg-1", now));

        int published = relayService.relayBatch(10);
        OutboxMessage failed = outboxRepository.findAll().getFirst();

        assertThat(published).isEqualTo(0);
        assertThat(failed.attemptCount()).isEqualTo(1);
        assertThat(failed.lastError()).contains("temporary network");
        assertThat(failed.nextAttemptAt()).isEqualTo(now.plusMillis(1000));
        assertThat(failed.deadLetteredAt()).isNull();
    }

    @Test
    void deadLettersWhenMaxAttemptsReached() {
        // Verifies failed message is dead-lettered once retry attempts hit policy limit.
        InMemoryOutboxRepository outboxRepository = new InMemoryOutboxRepository();
        Instant now = Instant.parse("2026-02-15T10:00:00Z");
        ClockPort clock = () -> now;
        OutboxRelayService relayService = new OutboxRelayService(
                outboxRepository,
                message -> {
                    throw new IllegalStateException("broker down");
                },
                clock,
                1,
                1000,
                8000,
                100
        );

        outboxRepository.save(message("msg-1", now));

        int published = relayService.relayBatch(10);
        OutboxMessage deadLettered = outboxRepository.findAll().getFirst();

        assertThat(published).isEqualTo(0);
        assertThat(deadLettered.deadLetteredAt()).isEqualTo(now);
        assertThat(deadLettered.lastError()).contains("broker down");
    }

    private OutboxMessage message(String id, Instant now) {
        return new OutboxMessage(
                id,
                "Order",
                "order-1",
                "OrderCreated",
                "{}",
                1,
                now,
                now,
                null,
                0,
                null,
                now,
                null
        );
    }

    private static final class RecordingPublisher implements OutboxEventPublisher {
        private final List<OutboxMessage> published = new ArrayList<>();

        @Override
        public void publish(OutboxMessage message) {
            published.add(message);
        }

        List<OutboxMessage> published() {
            return List.copyOf(published);
        }
    }
}
