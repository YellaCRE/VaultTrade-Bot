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
    }
}
