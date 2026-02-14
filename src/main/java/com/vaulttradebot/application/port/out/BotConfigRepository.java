package com.vaulttradebot.application.port.out;

import com.vaulttradebot.domain.ops.Bot;
import java.util.Optional;

public interface BotConfigRepository {
    Optional<Bot> load();

    Bot save(Bot bot);
}
