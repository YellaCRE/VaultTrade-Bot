package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.port.out.ExchangeTradingPort;
import com.vaulttradebot.domain.common.Order;
import org.springframework.stereotype.Component;

@Component
public class PaperExchangeTradingAdapter implements ExchangeTradingPort {
    @Override
    public Order placeOrder(Order order) {
        // Paper mode immediately fills for predictable local behavior.
        order.markFilled();
        return order;
    }

    @Override
    public void cancelOrder(String orderId) {
        // No-op in local paper adapter.
    }
}
