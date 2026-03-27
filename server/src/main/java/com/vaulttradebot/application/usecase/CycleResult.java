package com.vaulttradebot.application.usecase;

public record CycleResult(boolean executed, boolean orderPlaced, String message) {
    public boolean failed() {
        return message != null && message.startsWith("cycle failed:");
    }

    public boolean skipped() {
        return !executed;
    }
}
