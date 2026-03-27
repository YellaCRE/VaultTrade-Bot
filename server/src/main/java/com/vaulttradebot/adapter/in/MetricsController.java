package com.vaulttradebot.adapter.in;

import com.vaulttradebot.application.port.in.BotQueryUseCase;
import com.vaulttradebot.application.query.MetricsSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {
    private final BotQueryUseCase botQueryUseCase;

    public MetricsController(BotQueryUseCase botQueryUseCase) {
        this.botQueryUseCase = botQueryUseCase;
    }

    @GetMapping
    public MetricsSnapshot metrics() {
        return botQueryUseCase.getMetrics();
    }
}
