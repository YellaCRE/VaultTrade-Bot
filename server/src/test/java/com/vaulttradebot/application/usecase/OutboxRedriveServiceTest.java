package com.vaulttradebot.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaulttradebot.adapter.out.InMemoryOutboxRepository;
import com.vaulttradebot.application.outbox.OutboxMessage;
import com.vaulttradebot.application.port.out.ClockPort;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OutboxRedriveServiceTest {

    @Test
    void redrivesDeadLettersIntoReadyQueue() {
        // Verifies dead-lettered messages are reset and re-queued for publishing.
        InMemoryOutboxRepository repository = new InMemoryOutboxRepository();
        Instant now = Instant.parse("2026-02-15T10:00:00Z");
        ClockPort clock = () -> now;

        repository.save(new OutboxMessage(
                "msg-1",
                "Order",
                "order-1",
                "OrderCreated",
                "{}",
                1,
                now,
                now,
                null,
                5,
                "broker down",
                null,
                now.minusSeconds(60)
        ));

        OutboxRedriveService redriveService = new OutboxRedriveService(repository, clock, 100);
        int redriven = redriveService.redriveBatch(10);

        assertThat(redriven).isEqualTo(1);
        OutboxMessage reset = repository.findAll().getFirst();
        assertThat(reset.deadLetteredAt()).isNull();
        assertThat(reset.attemptCount()).isEqualTo(0);
        assertThat(reset.lastError()).isNull();
        assertThat(reset.nextAttemptAt()).isEqualTo(now);
        assertThat(repository.findReadyToPublish(10, now)).hasSize(1);
    }

    @Test
    void redriveHonorsBatchLimit() {
        // Verifies redrive only resets up to the requested batch size.
        InMemoryOutboxRepository repository = new InMemoryOutboxRepository();
        Instant now = Instant.parse("2026-02-15T10:00:00Z");
        ClockPort clock = () -> now;

        repository.save(new OutboxMessage("msg-1", "Order", "order-1", "OrderCreated", "{}", 1, now, now, null, 5, "e1", null, now));
        repository.save(new OutboxMessage("msg-2", "Order", "order-2", "OrderCreated", "{}", 1, now, now, null, 5, "e2", null, now));

        OutboxRedriveService redriveService = new OutboxRedriveService(repository, clock, 100);
        int redriven = redriveService.redriveBatch(1);

        assertThat(redriven).isEqualTo(1);
        assertThat(repository.findDeadLettered(10)).hasSize(1);
    }
}
