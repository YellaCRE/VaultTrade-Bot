package com.vaulttradebot.domain.execution.vo;

public record StrategyId(String value) {
    public StrategyId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("strategyId must not be blank");
        }
    }

    /** Creates a StrategyId from a specific value. */
    public static StrategyId of(String value) {
        return new StrategyId(value);
    }

    /** Returns a default StrategyId when strategy linkage is absent. */
    public static StrategyId unassigned() {
        return new StrategyId("UNASSIGNED");
    }
}
