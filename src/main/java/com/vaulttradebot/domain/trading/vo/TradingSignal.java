package com.vaulttradebot.domain.trading.vo;

public record TradingSignal(SignalAction action, String reason) {
    /** Validates a simple signal payload used by order decision logic. */
    public TradingSignal {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
