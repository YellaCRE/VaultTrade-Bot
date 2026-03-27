package com.vaulttradebot.adapter.in;

import com.vaulttradebot.application.port.in.SchedulerControlUseCase;
import com.vaulttradebot.application.port.in.SchedulerQueryUseCase;
import com.vaulttradebot.application.query.SchedulerStatusSnapshot;
import com.vaulttradebot.application.usecase.CycleResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bot/scheduler")
public class SchedulerController {
    private final SchedulerQueryUseCase schedulerQueryUseCase;
    private final SchedulerControlUseCase schedulerControlUseCase;

    public SchedulerController(
            SchedulerQueryUseCase schedulerQueryUseCase,
            SchedulerControlUseCase schedulerControlUseCase
    ) {
        this.schedulerQueryUseCase = schedulerQueryUseCase;
        this.schedulerControlUseCase = schedulerControlUseCase;
    }

    @GetMapping
    public SchedulerStatusSnapshot status() {
        return schedulerQueryUseCase.schedulerStatus();
    }

    @PostMapping("/pause")
    public SchedulerStatusSnapshot pause() {
        return schedulerControlUseCase.pause();
    }

    @PostMapping("/resume")
    public SchedulerStatusSnapshot resume() {
        return schedulerControlUseCase.resume();
    }

    @PostMapping("/trigger")
    public CycleResult trigger() {
        return schedulerControlUseCase.triggerNow();
    }
}
