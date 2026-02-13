package com.vaulttradebot.interfaces.rest;

import com.vaulttradebot.application.port.in.BotControlUseCase;
import com.vaulttradebot.application.port.in.RunTradingCycleUseCase;
import com.vaulttradebot.application.service.CycleResult;
import com.vaulttradebot.domain.ops.BotStatusSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bot")
public class BotController {
    private final BotControlUseCase botControlUseCase;
    private final RunTradingCycleUseCase runTradingCycleUseCase;

    public BotController(BotControlUseCase botControlUseCase, RunTradingCycleUseCase runTradingCycleUseCase) {
        this.botControlUseCase = botControlUseCase;
        this.runTradingCycleUseCase = runTradingCycleUseCase;
    }

    @GetMapping("/status")
    public BotStatusSnapshot status() {
        return botControlUseCase.status();
    }

    @PostMapping("/start")
    public BotStatusSnapshot start() {
        return botControlUseCase.start();
    }

    @PostMapping("/stop")
    public BotStatusSnapshot stop() {
        return botControlUseCase.stop();
    }

    @PostMapping("/cycle")
    public CycleResult cycle() {
        return runTradingCycleUseCase.runCycle();
    }
}
