package com.vaulttradebot.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "vault.circuit-breaker")
public class VaultCircuitBreakerProperties {
    // Global defaults for lightweight in-memory breaker instances around external integrations.
    private boolean enabled = true;

    @Min(1)
    private int failureThreshold = 3;

    @Min(100)
    private long openDurationMs = 30_000L;

    @Min(1)
    private int halfOpenMaxCalls = 1;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    public long getOpenDurationMs() {
        return openDurationMs;
    }

    public void setOpenDurationMs(long openDurationMs) {
        this.openDurationMs = openDurationMs;
    }

    public int getHalfOpenMaxCalls() {
        return halfOpenMaxCalls;
    }

    public void setHalfOpenMaxCalls(int halfOpenMaxCalls) {
        this.halfOpenMaxCalls = halfOpenMaxCalls;
    }
}
