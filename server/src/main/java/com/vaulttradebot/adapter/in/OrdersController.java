package com.vaulttradebot.adapter.in;

import com.vaulttradebot.application.port.in.BotQueryUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrdersController {
    private final BotQueryUseCase botQueryUseCase;

    public OrdersController(BotQueryUseCase botQueryUseCase) {
        this.botQueryUseCase = botQueryUseCase;
    }

    @GetMapping
    public List<OrderResponse> list() {
        return botQueryUseCase.listOrders().stream()
                .map(OrderResponse::from)
                .toList();
    }
}
