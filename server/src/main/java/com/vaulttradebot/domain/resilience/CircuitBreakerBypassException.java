package com.vaulttradebot.domain.resilience;

// Wraps errors that should flow back to the caller without counting as breaker failures.
public class CircuitBreakerBypassException extends RuntimeException {
    public CircuitBreakerBypassException(RuntimeException cause) {
        super(cause);
    }

    @Override
    public synchronized RuntimeException getCause() {
        return (RuntimeException) super.getCause();
    }
}
