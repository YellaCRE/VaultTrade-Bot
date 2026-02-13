package com.vaulttradebot.application.port.out;

import com.vaulttradebot.domain.shared.order.Order;

public interface ExchangeTradingPort {
    Order placeOrder(Order order);

    void cancelOrder(String orderId);
}
