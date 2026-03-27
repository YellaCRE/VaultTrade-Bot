package com.vaulttradebot.domain.resilience;

public class CircuitBreakerOpenException extends RuntimeException {
    public CircuitBreakerOpenException(String name) {
        super("circuit breaker is open: " + name);
    }
}
