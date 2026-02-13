package com.vaulttradebot.application.port.out;

import com.vaulttradebot.domain.shared.bot.Bot;
import java.util.Optional;

public interface BotConfigRepository {
    Optional<Bot> load();

    Bot save(Bot bot);
}
