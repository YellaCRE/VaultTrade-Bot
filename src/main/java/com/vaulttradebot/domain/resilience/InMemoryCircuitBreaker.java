package com.vaulttradebot.domain.resilience;

import com.vaulttradebot.application.port.out.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class InMemoryCircuitBreaker implements CircuitBreaker {
    private final ClockPort clockPort;
    private final ConcurrentMap<String, BreakerStatus> breakers = new ConcurrentHashMap<>();
    private final int failureThreshold;
    private final Duration openDuration;
    private final int halfOpenMaxCalls;

    public InMemoryCircuitBreaker(
            ClockPort clockPort,
            com.vaulttradebot.config.VaultCircuitBreakerProperties properties
    ) {
        this.clockPort = clockPort;
        this.failureThreshold = properties.getFailureThreshold();
        this.openDuration = Duration.ofMillis(properties.getOpenDurationMs());
        this.halfOpenMaxCalls = properties.getHalfOpenMaxCalls();
    }

    @Override
    public <T> T execute(String name, Supplier<T> action) {
        BreakerStatus status = breakers.computeIfAbsent(name, ignored -> new BreakerStatus());
        synchronized (status) {
            // Reject fast while OPEN, and allow a limited probe once the cooldown expires.
            status.beforeCall(name, clockPort.now(), openDuration, halfOpenMaxCalls);
        }

        try {
            T result = action.get();
            synchronized (status) {
                // Any successful probe closes the breaker and resets the failure window.
                status.onSuccess();
            }
            return result;
        } catch (RuntimeException error) {
            synchronized (status) {
                // Count infrastructure failures and open the breaker once the threshold is reached.
                status.onFailure(clockPort.now(), error, failureThreshold, openDuration);
            }
            throw error;
        }
    }

    @Override
    public void execute(String name, Runnable action) {
        execute(name, () -> {
            action.run();
            return null;
        });
    }

    @Override
    public CircuitBreakerSnapshot snapshot(String name) {
        BreakerStatus status = breakers.computeIfAbsent(name, ignored -> new BreakerStatus());
        synchronized (status) {
            Instant nextAttemptAt = status.openedAt == null ? null : status.openedAt.plus(openDuration);
            return new CircuitBreakerSnapshot(
                    name,
                    status.state,
                    status.consecutiveFailures,
                    status.openedAt,
                    nextAttemptAt
            );
        }
    }

    private static final class BreakerStatus {
        private CircuitBreakerState state = CircuitBreakerState.CLOSED;
        private int consecutiveFailures = 0;
        private Instant openedAt;
        private int halfOpenCalls = 0;

        private void beforeCall(String name, Instant now, Duration openDuration, int halfOpenMaxCalls) {
            if (state == CircuitBreakerState.OPEN) {
                Instant retryAt = openedAt.plus(openDuration);
                if (now.isBefore(retryAt)) {
                    throw new CircuitBreakerOpenException(name);
                }
                // Move to HALF_OPEN after the cooldown so the next call acts as a probe.
                state = CircuitBreakerState.HALF_OPEN;
                halfOpenCalls = 0;
            }

            if (state == CircuitBreakerState.HALF_OPEN && halfOpenCalls >= halfOpenMaxCalls) {
                throw new CircuitBreakerOpenException(name);
            }

            if (state == CircuitBreakerState.HALF_OPEN) {
                halfOpenCalls++;
            }
        }

        private void onSuccess() {
            consecutiveFailures = 0;
            halfOpenCalls = 0;
            openedAt = null;
            state = CircuitBreakerState.CLOSED;
        }

        private void onFailure(Instant now, RuntimeException error, int failureThreshold, Duration openDuration) {
            if (error instanceof CircuitBreakerOpenException || error instanceof CircuitBreakerBypassException) {
                // Bypass errors are caller-visible failures but intentionally do not advance breaker state.
                return;
            }

            consecutiveFailures++;
            if (state == CircuitBreakerState.HALF_OPEN || consecutiveFailures >= failureThreshold) {
                // Reopen immediately if the recovery probe fails, or once normal failures pile up.
                state = CircuitBreakerState.OPEN;
                openedAt = now;
                halfOpenCalls = 0;
                return;
            }

            if (state == CircuitBreakerState.OPEN && openedAt != null && now.isAfter(openedAt.plus(openDuration))) {
                state = CircuitBreakerState.HALF_OPEN;
                halfOpenCalls = 0;
            }
        }
    }
}
