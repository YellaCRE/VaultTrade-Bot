package com.vaulttradebot.domain.resilience;

import java.util.function.Supplier;

public interface CircuitBreaker {
    <T> T execute(String name, Supplier<T> action);

    void execute(String name, Runnable action);

    CircuitBreakerSnapshot snapshot(String name);
}
