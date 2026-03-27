package com.vaulttradebot.domain.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.config.VaultCircuitBreakerProperties;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class InMemoryCircuitBreakerTest {

    @Test
    void opensAfterThresholdAndFastFailsUntilCooldownExpires() {
        // Verifies the breaker opens after repeated failures and recovers after the configured cooldown.
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-03-14T00:00:00Z"));
        InMemoryCircuitBreaker circuitBreaker = new InMemoryCircuitBreaker(clock(now), properties(2, 30_000L, 1));

        assertThatThrownBy(() -> circuitBreaker.execute("exchange", () -> {
            throw new IllegalStateException("temporary");
        })).isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> circuitBreaker.execute("exchange", () -> {
            throw new IllegalStateException("temporary");
        })).isInstanceOf(IllegalStateException.class);

        CircuitBreakerSnapshot opened = circuitBreaker.snapshot("exchange");
        assertThat(opened.state()).isEqualTo(CircuitBreakerState.OPEN);
        assertThat(opened.consecutiveFailures()).isEqualTo(2);

        assertThatThrownBy(() -> circuitBreaker.execute("exchange", () -> "ok"))
                .isInstanceOf(CircuitBreakerOpenException.class);

        now.set(now.get().plusSeconds(31));

        String result = circuitBreaker.execute("exchange", () -> "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(circuitBreaker.snapshot("exchange").state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void reopensWhenHalfOpenProbeFails() {
        // Verifies a failed half-open probe immediately transitions the breaker back to OPEN.
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-03-14T00:00:00Z"));
        InMemoryCircuitBreaker circuitBreaker = new InMemoryCircuitBreaker(clock(now), properties(1, 10_000L, 1));

        assertThatThrownBy(() -> circuitBreaker.execute("exchange", () -> {
            throw new IllegalStateException("down");
        })).isInstanceOf(IllegalStateException.class);

        now.set(now.get().plusSeconds(11));

        assertThatThrownBy(() -> circuitBreaker.execute("exchange", () -> {
            throw new IllegalStateException("still down");
        })).isInstanceOf(IllegalStateException.class);

        CircuitBreakerSnapshot reopened = circuitBreaker.snapshot("exchange");
        assertThat(reopened.state()).isEqualTo(CircuitBreakerState.OPEN);
        assertThat(reopened.openedAt()).isEqualTo(now.get());
    }

    private ClockPort clock(AtomicReference<Instant> now) {
        return now::get;
    }

    private VaultCircuitBreakerProperties properties(int threshold, long openDurationMs, int halfOpenMaxCalls) {
        VaultCircuitBreakerProperties properties = new VaultCircuitBreakerProperties();
        properties.setFailureThreshold(threshold);
        properties.setOpenDurationMs(openDurationMs);
        properties.setHalfOpenMaxCalls(halfOpenMaxCalls);
        return properties;
    }
}
