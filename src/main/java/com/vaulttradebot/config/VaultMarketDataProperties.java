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

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
