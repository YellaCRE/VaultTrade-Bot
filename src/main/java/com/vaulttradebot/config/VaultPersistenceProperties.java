package com.vaulttradebot.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "vault.persistence")
public class VaultPersistenceProperties {

    @NotNull
    private Mode mode = Mode.JDBC;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public enum Mode {
        MEMORY,
        JDBC
    }
}
