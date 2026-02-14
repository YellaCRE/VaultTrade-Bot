package com.vaulttradebot.application.port.in;

import com.vaulttradebot.application.query.BotStatusSnapshot;

public interface BotControlUseCase {
    BotStatusSnapshot status();

    BotStatusSnapshot start();

    BotStatusSnapshot stop();
}
