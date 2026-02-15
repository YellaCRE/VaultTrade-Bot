package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.port.out.OrderRepository;
import com.vaulttradebot.domain.execution.Order;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InMemoryOrderRepository implements OrderRepository {
    private final List<Order> orders = java.util.Collections.synchronizedList(new ArrayList<>());

    @Override
    public Order save(Order order) {
        synchronized (orders) {
            int index = -1;
            for (int i = 0; i < orders.size(); i++) {
                if (orders.get(i).id().equals(order.id())) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) {
                orders.set(index, order);
            } else {
                orders.add(order);
            }
        }
        return order;
    }

    @Override
    public List<Order> findAll() {
        synchronized (orders) {
            return List.copyOf(orders);
        }
    }
}
