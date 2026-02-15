package com.vaulttradebot.application.usecase;

import com.vaulttradebot.application.outbox.OutboxMessage;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.port.out.OrderOutboxTransactionPort;
import com.vaulttradebot.application.port.out.OrderRepository;
import com.vaulttradebot.application.port.out.OutboxRepository;
import com.vaulttradebot.application.port.out.OutboxPayloadSerializer;
import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.domain.execution.event.OrderDomainEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderPersistenceService {
    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final OrderOutboxTransactionPort transactionPort;
    private final ClockPort clockPort;
    private final OutboxPayloadSerializer outboxPayloadSerializer;

    public OrderPersistenceService(
            OrderRepository orderRepository,
            OutboxRepository outboxRepository,
            OrderOutboxTransactionPort transactionPort,
            ClockPort clockPort,
            OutboxPayloadSerializer outboxPayloadSerializer
    ) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.transactionPort = transactionPort;
        this.clockPort = clockPort;
        this.outboxPayloadSerializer = outboxPayloadSerializer;
    }

    /** Persists order and outbox messages in one atomic transaction boundary. */
    public Order persist(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        List<OrderDomainEvent> domainEvents = order.pullDomainEvents();
        Instant now = clockPort.now();
        try {
            transactionPort.execute(() -> {
                orderRepository.save(order);
                for (OrderDomainEvent event : domainEvents) {
                    outboxRepository.save(OutboxMessage.fromOrderEvent(
                            event,
                            outboxPayloadSerializer.serialize(event),
                            outboxPayloadSerializer.payloadVersion(),
                            now
                    ));
                }
            });
        } catch (RuntimeException e) {
            order.restoreDomainEvents(domainEvents);
            throw e;
        }
        return order;
    }
}
