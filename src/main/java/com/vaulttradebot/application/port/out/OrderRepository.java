package com.vaulttradebot.application.port.out;

import com.vaulttradebot.domain.shared.Order;
import java.util.List;

public interface OrderRepository {
    Order save(Order order);

    List<Order> findAll();

    boolean existsByIdempotencyKey(String idempotencyKey);

    void rememberIdempotencyKey(String idempotencyKey);
}
