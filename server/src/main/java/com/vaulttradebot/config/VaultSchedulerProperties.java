package com.vaulttradebot.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "vault.scheduler")
public class VaultSchedulerProperties {
    private boolean enabled = true;

    @NotBlank
    private String cron = "*/5 * * * * *";

    @NotBlank
    private String zone = "Asia/Seoul";

    @Min(100)
    private long pollDelayMs = 1000L;

    @Min(0)
    private long misfireThresholdMs = 15_000L;

    @Min(0)
    private int maxRetryAttempts = 3;

    @Min(100)
    private long retryBaseDelayMs = 2000L;

    @Min(100)
    private long retryMaxDelayMs = 30_000L;

    private MisfirePolicy misfirePolicy = MisfirePolicy.FIRE_ONCE_NOW;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public long getPollDelayMs() {
        return pollDelayMs;
    }

    public void setPollDelayMs(long pollDelayMs) {
        this.pollDelayMs = pollDelayMs;
    }

    public long getMisfireThresholdMs() {
        return misfireThresholdMs;
    }

    public void setMisfireThresholdMs(long misfireThresholdMs) {
        this.misfireThresholdMs = misfireThresholdMs;
    }

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public void setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }

    public long getRetryBaseDelayMs() {
        return retryBaseDelayMs;
    }

    public void setRetryBaseDelayMs(long retryBaseDelayMs) {
        this.retryBaseDelayMs = retryBaseDelayMs;
    }

    public long getRetryMaxDelayMs() {
        return retryMaxDelayMs;
    }

    public void setRetryMaxDelayMs(long retryMaxDelayMs) {
        this.retryMaxDelayMs = retryMaxDelayMs;
    }

    public MisfirePolicy getMisfirePolicy() {
        return misfirePolicy;
    }

    public void setMisfirePolicy(MisfirePolicy misfirePolicy) {
        this.misfirePolicy = misfirePolicy;
    }

    public enum MisfirePolicy {
        FIRE_ONCE_NOW,
        SKIP
    }
}
