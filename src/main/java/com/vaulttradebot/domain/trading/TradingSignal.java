package com.vaulttradebot.domain.trading;

public record TradingSignal(SignalAction action, String reason) {
    public TradingSignal {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
