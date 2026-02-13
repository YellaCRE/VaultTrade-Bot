package com.vaulttradebot.application.port.in;

import com.vaulttradebot.domain.shared.bot.BotStatusSnapshot;

public interface BotControlUseCase {
    BotStatusSnapshot status();

    BotStatusSnapshot start();

    BotStatusSnapshot stop();
}
