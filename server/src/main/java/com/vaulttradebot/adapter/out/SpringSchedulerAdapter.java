package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.usecase.TradingCycleSchedulerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SpringSchedulerAdapter {
    private final TradingCycleSchedulerService tradingCycleSchedulerService;

    public SpringSchedulerAdapter(TradingCycleSchedulerService tradingCycleSchedulerService) {
        this.tradingCycleSchedulerService = tradingCycleSchedulerService;
    }

    @Scheduled(fixedDelayString = "${vault.scheduler.poll-delay-ms:1000}")
    public void run() {
        // Keep the framework adapter thin and delegate policy inward.
        tradingCycleSchedulerService.poll();
    }
}
