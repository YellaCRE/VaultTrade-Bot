package com.vaulttradebot.application.service;

public record CycleResult(boolean executed, boolean orderPlaced, String message) {
}
