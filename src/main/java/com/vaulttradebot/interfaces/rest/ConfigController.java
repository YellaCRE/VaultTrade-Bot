package com.vaulttradebot.interfaces.rest;

import com.vaulttradebot.application.port.in.BotConfigUseCase;
import com.vaulttradebot.domain.shared.bot.BotConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigController {
    private final BotConfigUseCase botConfigUseCase;

    public ConfigController(BotConfigUseCase botConfigUseCase) {
        this.botConfigUseCase = botConfigUseCase;
    }

    @GetMapping
    public BotConfig get() {
        return botConfigUseCase.getConfig();
    }

    @PutMapping
    public BotConfig update(@RequestBody BotConfig config) {
        return botConfigUseCase.updateConfig(config);
    }
}
