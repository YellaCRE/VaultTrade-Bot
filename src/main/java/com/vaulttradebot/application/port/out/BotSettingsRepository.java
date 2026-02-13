package com.vaulttradebot.application.port.out;

import com.vaulttradebot.domain.shared.bot.BotConfig;

public interface BotSettingsRepository {
    BotConfig load();

    BotConfig save(BotConfig config);
}
