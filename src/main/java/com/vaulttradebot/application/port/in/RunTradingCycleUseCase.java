package com.vaulttradebot.application.port.in;

import com.vaulttradebot.application.service.CycleResult;

public interface RunTradingCycleUseCase {
    CycleResult runCycle();
}
