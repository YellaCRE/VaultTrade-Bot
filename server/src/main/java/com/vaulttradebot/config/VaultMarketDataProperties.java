package com.vaulttradebot.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "vault.market-data")
public class VaultMarketDataProperties {
    @NotBlank
    private String provider = "upbit";

    private final Upbit upbit = new Upbit();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Upbit getUpbit() {
        return upbit;
    }

    public static class Upbit {
        @NotBlank
        private String baseUrl = "https://api.upbit.com";
        private final Retry retry = new Retry();

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Retry getRetry() {
            return retry;
        }
    }

    public static class Retry {
        private int maxAttempts = 4;
        private long baseDelayMs = 300L;
        private long maxDelayMs = 3_000L;
        private long rateLimitDelayMs = 1_000L;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getBaseDelayMs() {
            return baseDelayMs;
        }

        public void setBaseDelayMs(long baseDelayMs) {
            this.baseDelayMs = baseDelayMs;
        }

        public long getMaxDelayMs() {
            return maxDelayMs;
        }

        public void setMaxDelayMs(long maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
        }

        public long getRateLimitDelayMs() {
            return rateLimitDelayMs;
        }

        public void setRateLimitDelayMs(long rateLimitDelayMs) {
            this.rateLimitDelayMs = rateLimitDelayMs;
        }
    }
}
