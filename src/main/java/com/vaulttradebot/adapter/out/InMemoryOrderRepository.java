package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.port.out.OrderRepository;
import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.domain.execution.vo.OrderStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "vault.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryOrderRepository implements OrderRepository {
    private final List<Order> orders = java.util.Collections.synchronizedList(new ArrayList<>());

    @Override
    public Order save(Order order) {
        Order stored = copy(order);
        synchronized (orders) {
            int index = -1;
            for (int i = 0; i < orders.size(); i++) {
                if (orders.get(i).id().equals(order.id())) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) {
                orders.set(index, stored);
            } else {
                orders.add(stored);
            }
        }
        return copy(stored);
    }

    @Override
    public List<Order> findAll() {
        synchronized (orders) {
            return orders.stream().map(InMemoryOrderRepository::copy).toList();
        }
    }

    @Override
    public List<Order> findActiveOrders() {
        synchronized (orders) {
            return orders.stream()
                    .filter(order -> order.status() == OrderStatus.NEW
                            || order.status() == OrderStatus.OPEN
                            || order.status() == OrderStatus.PARTIAL_FILLED
                            || order.status() == OrderStatus.CANCEL_REQUESTED)
                    .map(InMemoryOrderRepository::copy)
                    .toList();
        }
    }

    @Override
    public Optional<Order> findById(String orderId) {
        synchronized (orders) {
            return orders.stream()
                    .filter(order -> order.id().equals(orderId))
                    .map(InMemoryOrderRepository::copy)
                    .findFirst();
        }
    }

    List<Order> snapshot() {
        synchronized (orders) {
            return orders.stream().map(InMemoryOrderRepository::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
    }

    void restore(List<Order> snapshot) {
        synchronized (orders) {
            orders.clear();
            orders.addAll(snapshot.stream().map(InMemoryOrderRepository::copy).toList());
        }
    }

    private static Order copy(Order order) {
        return Order.rehydrate(
                order.orderId(),
                order.market(),
                order.orderType(),
                order.side(),
                order.originalQuantity(),
                order.avgPriceValue(),
                order.minimumProfitPrice(),
                order.strategyId(),
                order.idempotencyKey(),
                order.createdAt(),
                order.status(),
                order.executedQuantity(),
                order.executedAmount(),
                order.executedFee(),
                order.exchangeOrderId(),
                order.version()
        );
    }
}
