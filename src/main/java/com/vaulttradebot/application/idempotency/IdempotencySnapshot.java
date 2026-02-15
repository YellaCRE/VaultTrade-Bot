package com.vaulttradebot.application.idempotency;

import com.vaulttradebot.application.usecase.CycleResult;

public record IdempotencySnapshot(
        boolean executed,
        boolean orderPlaced,
        String message
) {
    public static IdempotencySnapshot from(CycleResult result) {
        return new IdempotencySnapshot(result.executed(), result.orderPlaced(), result.message());
    }

    public CycleResult toCycleResult() {
        return new CycleResult(executed, orderPlaced, message);
    }
}
