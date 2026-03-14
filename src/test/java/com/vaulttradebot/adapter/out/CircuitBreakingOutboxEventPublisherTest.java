package com.vaulttradebot.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaulttradebot.application.outbox.OutboxMessage;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.port.out.NotificationPort;
import com.vaulttradebot.application.port.out.OutboxEventPublisher;
import com.vaulttradebot.config.VaultCircuitBreakerProperties;
import com.vaulttradebot.domain.resilience.CircuitBreakerOpenException;
import com.vaulttradebot.domain.resilience.InMemoryCircuitBreaker;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CircuitBreakingOutboxEventPublisherTest {

    @Test
    void opensAndBlocksPublishCallsAfterRepeatedFailures() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-03-14T00:00:00Z"));
        InMemoryCircuitBreaker circuitBreaker = new InMemoryCircuitBreaker(now::get, properties(true, 2, 30_000L, 1));
        RecordingNotificationPort notifications = new RecordingNotificationPort();
        AtomicInteger attempts = new AtomicInteger(0);
        OutboxEventPublisher delegate = message -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("broker unavailable");
        };

        CircuitBreakingOutboxEventPublisher publisher = new CircuitBreakingOutboxEventPublisher(
                delegate,
                circuitBreaker,
                notifications,
                properties(true, 2, 30_000L, 1)
        );

        assertThatThrownBy(() -> publisher.publish(message("msg-1", now.get())))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> publisher.publish(message("msg-2", now.get())))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> publisher.publish(message("msg-3", now.get())))
                .isInstanceOf(CircuitBreakerOpenException.class);

        assertThat(attempts.get()).isEqualTo(2);
        assertThat(notifications.messages()).anyMatch(value -> value.contains("CLOSED -> OPEN"));
    }

    @Test
    void bypassesCircuitBreakerWhenDisabled() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-03-14T00:00:00Z"));
        InMemoryCircuitBreaker circuitBreaker = new InMemoryCircuitBreaker(clock(now), properties(false, 2, 30_000L, 1));
        RecordingNotificationPort notifications = new RecordingNotificationPort();
        AtomicInteger attempts = new AtomicInteger(0);
        OutboxEventPublisher delegate = message -> attempts.incrementAndGet();

        CircuitBreakingOutboxEventPublisher publisher = new CircuitBreakingOutboxEventPublisher(
                delegate,
                circuitBreaker,
                notifications,
                properties(false, 2, 30_000L, 1)
        );

        publisher.publish(message("msg-1", now.get()));
        publisher.publish(message("msg-2", now.get()));

        assertThat(attempts.get()).isEqualTo(2);
        assertThat(notifications.messages()).isEmpty();
    }

    private ClockPort clock(AtomicReference<Instant> now) {
        return now::get;
    }

    private VaultCircuitBreakerProperties properties(boolean enabled, int threshold, long openDurationMs, int halfOpenMaxCalls) {
        VaultCircuitBreakerProperties properties = new VaultCircuitBreakerProperties();
        properties.setEnabled(enabled);
        properties.setFailureThreshold(threshold);
        properties.setOpenDurationMs(openDurationMs);
        properties.setHalfOpenMaxCalls(halfOpenMaxCalls);
        return properties;
    }

    private OutboxMessage message(String id, Instant now) {
        return new OutboxMessage(
                id,
                "TradingCycle",
                "cycle-1",
                "OrderCommandRequested",
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

    private static final class RecordingNotificationPort implements NotificationPort {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void notify(String message) {
            messages.add(message);
        }

        List<String> messages() {
            return List.copyOf(messages);
        }
    }
}
