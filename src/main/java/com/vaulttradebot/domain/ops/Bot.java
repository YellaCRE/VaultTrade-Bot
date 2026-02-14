package com.vaulttradebot.domain.ops;

public class Bot {
    private final String id;
    private BotConfig config;
    private BotStatus status;

    public Bot(String id, BotConfig config, BotStatus status) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (config == null || status == null) {
            throw new IllegalArgumentException("config and status must not be null");
        }
        this.id = id;
        this.config = config;
        this.status = status;
    }

    public void updateConfig(BotConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
    }

    public void updateStatus(BotStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
    }

    public String id() {
        return id;
    }

    public BotConfig config() {
        return config;
    }

    public BotStatus status() {
        return status;
    }
}
