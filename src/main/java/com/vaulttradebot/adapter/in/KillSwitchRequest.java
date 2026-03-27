package com.vaulttradebot.adapter.in;

public record KillSwitchRequest(
        String reason,
        Boolean cancelActiveOrders
) {
    public String normalizedReason() {
        if (reason == null || reason.isBlank()) {
            return "manual kill switch activation";
        }
        return reason.trim();
    }

    public boolean shouldCancelActiveOrders() {
        return cancelActiveOrders == null || cancelActiveOrders;
    }
}
