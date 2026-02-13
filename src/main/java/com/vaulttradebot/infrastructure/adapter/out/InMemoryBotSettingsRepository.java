package com.vaulttradebot.infrastructure.adapter.out;

import com.vaulttradebot.application.port.out.BotSettingsRepository;
import com.vaulttradebot.domain.shared.bot.BotConfig;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class InMemoryBotSettingsRepository implements BotSettingsRepository {
    private final AtomicReference<BotConfig> config = new AtomicReference<>(BotConfig.defaultConfig());

    @Override
    public BotConfig load() {
        return config.get();
    }

    @Override
    public BotConfig save(BotConfig config) {
        this.config.set(config);
        return config;
    }
}
