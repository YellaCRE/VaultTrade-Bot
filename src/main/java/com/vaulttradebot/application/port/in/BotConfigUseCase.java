package com.vaulttradebot.application.port.in;

import com.vaulttradebot.domain.shared.bot.BotConfig;

public interface BotConfigUseCase {
    BotConfig getConfig();

    BotConfig updateConfig(BotConfig config);
}
