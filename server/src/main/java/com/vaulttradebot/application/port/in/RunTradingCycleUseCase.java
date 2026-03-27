package com.vaulttradebot.application.port.in;

import com.vaulttradebot.application.usecase.CycleResult;

public interface RunTradingCycleUseCase {
    CycleResult runCycle();
}
