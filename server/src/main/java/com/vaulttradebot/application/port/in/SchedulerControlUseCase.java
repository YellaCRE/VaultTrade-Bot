package com.vaulttradebot.application.port.in;

import com.vaulttradebot.application.query.SchedulerStatusSnapshot;
import com.vaulttradebot.application.usecase.CycleResult;

public interface SchedulerControlUseCase {
    SchedulerStatusSnapshot pause();

    SchedulerStatusSnapshot resume();

    CycleResult triggerNow();
}
