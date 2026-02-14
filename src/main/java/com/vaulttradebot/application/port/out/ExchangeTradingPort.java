package com.vaulttradebot.application.port.out;

import com.vaulttradebot.domain.common.Order;

public interface ExchangeTradingPort {
    Order placeOrder(Order order);

    void cancelOrder(String orderId);
}
