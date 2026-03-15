package com.vaulttradebot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vault.trading")
public class VaultTradingProperties {
    // Separate trading provider selection from market-data selection so paper/live execution can be switched independently.
    private String provider = "paper";
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
        private String baseUrl = "https://api.upbit.com";
        private String accessKey = "";
        private String secretKey = "";
        private final Retry retry = new Retry();

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public Retry getRetry() {
            return retry;
        }
    }

    public static class Retry {
        private int maxAttempts = 2;
        private long baseDelayMs = 500L;
        private long maxDelayMs = 5_000L;
        private long rateLimitDelayMs = 1_500L;

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
