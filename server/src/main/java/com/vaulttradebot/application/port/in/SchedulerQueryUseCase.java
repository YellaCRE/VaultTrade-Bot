package com.vaulttradebot.application.port.in;

import com.vaulttradebot.application.query.SchedulerStatusSnapshot;

public interface SchedulerQueryUseCase {
    SchedulerStatusSnapshot schedulerStatus();
}
