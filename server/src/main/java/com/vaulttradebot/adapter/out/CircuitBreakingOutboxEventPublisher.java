package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.outbox.OutboxMessage;
import com.vaulttradebot.application.port.out.NotificationPort;
import com.vaulttradebot.application.port.out.OutboxEventPublisher;
import com.vaulttradebot.config.VaultCircuitBreakerProperties;
import com.vaulttradebot.domain.resilience.CircuitBreaker;
import com.vaulttradebot.domain.resilience.CircuitBreakerOpenException;
import com.vaulttradebot.domain.resilience.CircuitBreakerSnapshot;
import com.vaulttradebot.domain.resilience.CircuitBreakerState;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class CircuitBreakingOutboxEventPublisher implements OutboxEventPublisher {
    private static final String BREAKER_NAME = "outbox-event-publisher";

    private final OutboxEventPublisher delegate;
    private final CircuitBreaker circuitBreaker;
    private final NotificationPort notificationPort;
    private final VaultCircuitBreakerProperties properties;

    public CircuitBreakingOutboxEventPublisher(
            @Qualifier("loggingOutboxEventPublisher") OutboxEventPublisher delegate,
            CircuitBreaker circuitBreaker,
            NotificationPort notificationPort,
            VaultCircuitBreakerProperties properties
    ) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
        this.notificationPort = notificationPort;
        this.properties = properties;
    }

    @Override
    public void publish(OutboxMessage message) {
        if (!properties.isEnabled()) {
            delegate.publish(message);
            return;
        }

        CircuitBreakerSnapshot before = circuitBreaker.snapshot(BREAKER_NAME);
        try {
            // Guard the outbound publish path so repeated broker failures do not hammer the dependency.
            circuitBreaker.execute(BREAKER_NAME, () -> delegate.publish(message));
        } catch (CircuitBreakerOpenException openError) {
            notifyStateChange(before.state(), CircuitBreakerState.OPEN, openError.getMessage());
            throw openError;
        } catch (RuntimeException error) {
            CircuitBreakerSnapshot after = circuitBreaker.snapshot(BREAKER_NAME);
            notifyStateChange(before.state(), after.state(), error.getMessage());
            throw error;
        }
    }

    private void notifyStateChange(CircuitBreakerState before, CircuitBreakerState after, String detail) {
        if (before == after) {
            return;
        }
        // Emit an operator-visible signal only when the breaker actually changes state.
        notificationPort.notify("Circuit breaker transitioned " + before + " -> " + after + ": " + detail);
    }
}
