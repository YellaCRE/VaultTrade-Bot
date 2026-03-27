package com.vaulttradebot.application.usecase;

import com.vaulttradebot.application.port.out.ExchangeTradingPort;
import com.vaulttradebot.application.port.out.OrderRepository;
import com.vaulttradebot.domain.execution.Order;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderFillSyncService {
    private final OrderRepository orderRepository;
    private final ExchangeTradingPort exchangeTradingPort;
    private final OrderPersistenceService orderPersistenceService;

    public OrderFillSyncService(
            OrderRepository orderRepository,
            ExchangeTradingPort exchangeTradingPort,
            OrderPersistenceService orderPersistenceService
    ) {
        this.orderRepository = orderRepository;
        this.exchangeTradingPort = exchangeTradingPort;
        this.orderPersistenceService = orderPersistenceService;
    }

    /** Refreshes active exchange orders and persists only when exchange state changed. */
    public void syncActiveOrders() {
        List<Order> activeOrders = orderRepository.findActiveOrders();
        for (Order order : activeOrders) {
            if (order.exchangeOrderId() == null || order.exchangeOrderId().isBlank()) {
                continue;
            }

            OrderSnapshot before = OrderSnapshot.capture(order);
            Order refreshed = exchangeTradingPort.refreshOrder(order);
            if (before.hasChanged(refreshed)) {
                orderPersistenceService.persist(refreshed);
            }
        }
    }

    private record OrderSnapshot(
            String status,
            String exchangeOrderId,
            java.math.BigDecimal executedQuantity,
            java.math.BigDecimal executedAmount,
            long version
    ) {
        static OrderSnapshot capture(Order order) {
            return new OrderSnapshot(
                    order.status().name(),
                    order.exchangeOrderId(),
                    order.executedQuantity().value(),
                    order.executedAmount().amount(),
                    order.version()
            );
        }

        boolean hasChanged(Order order) {
            return !status.equals(order.status().name())
                    || !java.util.Objects.equals(exchangeOrderId, order.exchangeOrderId())
                    || executedQuantity.compareTo(order.executedQuantity().value()) != 0
                    || executedAmount.compareTo(order.executedAmount().amount()) != 0
                    || version != order.version();
        }
    }
}
