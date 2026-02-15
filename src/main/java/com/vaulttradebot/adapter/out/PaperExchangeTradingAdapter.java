package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.port.out.ExchangeTradingPort;
import com.vaulttradebot.domain.common.vo.Quantity;
import com.vaulttradebot.domain.execution.ExecutionTrade;
import com.vaulttradebot.domain.execution.Order;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PaperExchangeTradingAdapter implements ExchangeTradingPort {
    @Override
    public Order placeOrder(Order order) {
        // Paper mode: exchange accepts immediately and fully fills.
        order.acceptByExchange();
        order.execute(new ExecutionTrade(
                UUID.randomUUID().toString(),
                order.price(),
                Quantity.of(order.quantity()),
                Instant.now()
        ));
        return order;
    }

    @Override
    public void cancelOrder(String orderId) {
        // No-op in local paper adapter.
    }
}
