package com.vaulttradebot.domain.shared;

import java.math.BigDecimal;

public class Bot {
    private final String id;
    private boolean enabled;
    private BigDecimal maxPositionRatio;
    private BigDecimal dailyLossLimitRatio;

    public Bot(String id, boolean enabled, BigDecimal maxPositionRatio, BigDecimal dailyLossLimitRatio) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (maxPositionRatio == null || dailyLossLimitRatio == null) {
            throw new IllegalArgumentException("risk ratios must not be null");
        }
        this.id = id;
        this.enabled = enabled;
        this.maxPositionRatio = maxPositionRatio;
        this.dailyLossLimitRatio = dailyLossLimitRatio;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public void updateRiskPolicy(BigDecimal maxPositionRatio, BigDecimal dailyLossLimitRatio) {
        if (maxPositionRatio == null || dailyLossLimitRatio == null) {
            throw new IllegalArgumentException("risk ratios must not be null");
        }
        this.maxPositionRatio = maxPositionRatio;
        this.dailyLossLimitRatio = dailyLossLimitRatio;
    }

    public String id() {
        return id;
    }

    public boolean enabled() {
        return enabled;
    }

    public BigDecimal maxPositionRatio() {
        return maxPositionRatio;
    }

    public BigDecimal dailyLossLimitRatio() {
        return dailyLossLimitRatio;
    }
}
