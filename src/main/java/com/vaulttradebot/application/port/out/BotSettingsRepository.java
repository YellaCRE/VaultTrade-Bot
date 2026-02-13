package com.vaulttradebot.application.port.out;

import com.vaulttradebot.domain.ops.BotConfig;

public interface BotSettingsRepository {
    BotConfig load();

    BotConfig save(BotConfig config);
}
