package com.vaulttradebot.adapter.in;

import com.vaulttradebot.application.port.in.BotControlUseCase;
import com.vaulttradebot.application.port.in.RunTradingCycleUseCase;
import com.vaulttradebot.application.usecase.CycleResult;
import com.vaulttradebot.application.query.BotStatusSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/kill-switch")
    public BotStatusSnapshot killSwitchStatus() {
        return botControlUseCase.status();
    }

    @PostMapping("/kill-switch")
    public BotStatusSnapshot activateKillSwitch(@RequestBody(required = false) KillSwitchRequest request) {
        KillSwitchRequest payload = request == null ? new KillSwitchRequest(null, null) : request;
        return botControlUseCase.activateKillSwitch(payload.normalizedReason(), payload.shouldCancelActiveOrders());
    }

    @PostMapping("/kill-switch/release")
    public BotStatusSnapshot releaseKillSwitch() {
        return botControlUseCase.releaseKillSwitch();
    }

    @PostMapping("/cycle")
    public CycleResult cycle() {
        return runTradingCycleUseCase.runCycle();
    }
}
