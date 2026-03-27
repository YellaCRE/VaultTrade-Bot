package com.vaulttradebot.domain.ops;

public class KillSwitchActiveException extends IllegalStateException {
    public KillSwitchActiveException(String reason) {
        super(reason == null || reason.isBlank()
                ? "kill switch is active"
                : "kill switch is active: " + reason);
    }
}
