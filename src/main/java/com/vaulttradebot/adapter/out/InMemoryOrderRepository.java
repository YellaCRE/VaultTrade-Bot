package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.port.out.OrderRepository;
import com.vaulttradebot.domain.common.Order;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryOrderRepository implements OrderRepository {
    private final List<Order> orders = java.util.Collections.synchronizedList(new ArrayList<>());
    private final Set<String> idempotencyKeys = ConcurrentHashMap.newKeySet();

    @Override
    public Order save(Order order) {
        orders.add(order);
        return order;
    }

    @Override
    public List<Order> findAll() {
        synchronized (orders) {
            return List.copyOf(orders);
        }
    }

    @Override
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        return idempotencyKeys.contains(idempotencyKey);
    }

    @Override
    public void rememberIdempotencyKey(String idempotencyKey) {
        idempotencyKeys.add(idempotencyKey);
    }
}
