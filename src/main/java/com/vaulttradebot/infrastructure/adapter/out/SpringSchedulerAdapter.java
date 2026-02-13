package com.vaulttradebot.infrastructure.adapter.out;

import com.vaulttradebot.application.port.in.RunTradingCycleUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SpringSchedulerAdapter {
    private final RunTradingCycleUseCase runTradingCycleUseCase;

    public SpringSchedulerAdapter(RunTradingCycleUseCase runTradingCycleUseCase) {
        this.runTradingCycleUseCase = runTradingCycleUseCase;
    }

    @Scheduled(fixedDelayString = "${bot.cycle-delay-ms:5000}")
    public void run() {
        runTradingCycleUseCase.runCycle();
    }
}
