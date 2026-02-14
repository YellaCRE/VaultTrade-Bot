package com.vaulttradebot.application.usecase;

public record CycleResult(boolean executed, boolean orderPlaced, String message) {
}
