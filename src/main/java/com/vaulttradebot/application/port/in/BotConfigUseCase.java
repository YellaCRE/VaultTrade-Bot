package com.vaulttradebot.application.port.in;

import com.vaulttradebot.domain.ops.BotConfig;

public interface BotConfigUseCase {
    BotConfig getConfig();

    BotConfig updateConfig(BotConfig config);
}
