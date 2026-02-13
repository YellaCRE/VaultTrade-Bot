package com.vaulttradebot.interfaces.rest;

import com.vaulttradebot.application.port.in.BotQueryUseCase;
import com.vaulttradebot.domain.portfolio.PortfolioSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {
    private final BotQueryUseCase botQueryUseCase;

    public PortfolioController(BotQueryUseCase botQueryUseCase) {
        this.botQueryUseCase = botQueryUseCase;
    }

    @GetMapping("/snapshot")
    public PortfolioSnapshot snapshot() {
        return botQueryUseCase.getPortfolioSnapshot();
    }
}
