package com.vaulttradebot.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaulttradebot.adapter.out.InMemoryOrderOutboxTransactionAdapter;
import com.vaulttradebot.adapter.out.InMemoryOrderRepository;
import com.vaulttradebot.adapter.out.InMemoryOutboxRepository;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.port.out.OutboxPayloadSerializer;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.domain.execution.event.OrderDomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderPersistenceServiceTest {

    @Test
    void persistsOrderAndOutboxAtomicallyOnSuccess() {
        // Verifies one successful transaction writes both order and outbox message.
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        InMemoryOutboxRepository outboxRepository = new InMemoryOutboxRepository();
        ClockPort clock = () -> Instant.parse("2026-02-15T10:00:00Z");
        OrderPersistenceService service = new OrderPersistenceService(
                orderRepository,
                outboxRepository,
                new InMemoryOrderOutboxTransactionAdapter(orderRepository, outboxRepository),
                clock,
                testSerializer()
        );

        Order order = Order.create(
                Market.of("KRW-BTC"),
                Side.BUY,
                new BigDecimal("0.00200000"),
                Money.krw(new BigDecimal("50000000")),
                clock.now()
        );

        service.persist(order);

        assertThat(orderRepository.findAll()).hasSize(1);
        assertThat(outboxRepository.findAll()).hasSize(1);
        assertThat(outboxRepository.findAll().getFirst().aggregateId()).isEqualTo(order.id());
        assertThat(order.pullDomainEvents()).isEmpty();
    }

    @Test
    void rollsBackOrderWriteWhenOutboxSaveFails() {
        // Verifies outbox failure rolls back order persistence and restores domain events.
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        InMemoryOutboxRepository outboxRepository = new InMemoryOutboxRepository();
        outboxRepository.setFailOnSave(true);

        ClockPort clock = () -> Instant.parse("2026-02-15T10:00:00Z");
        OrderPersistenceService service = new OrderPersistenceService(
                orderRepository,
                outboxRepository,
                new InMemoryOrderOutboxTransactionAdapter(orderRepository, outboxRepository),
                clock,
                testSerializer()
        );

        Order order = Order.create(
                Market.of("KRW-BTC"),
                Side.BUY,
                new BigDecimal("0.00200000"),
                Money.krw(new BigDecimal("50000000")),
                clock.now()
        );

        assertThatThrownBy(() -> service.persist(order))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forced outbox failure");

        assertThat(orderRepository.findAll()).isEmpty();
        assertThat(outboxRepository.findAll()).isEmpty();

        List<OrderDomainEvent> restored = order.pullDomainEvents();
        assertThat(restored).hasSize(1);
        assertThat(restored.getFirst().orderId()).isEqualTo(order.id());
    }

    private OutboxPayloadSerializer testSerializer() {
        return new OutboxPayloadSerializer() {
            @Override
            public String serialize(OrderDomainEvent event) {
                return "{\"eventType\":\"" + event.getClass().getSimpleName() + "\"}";
            }

            @Override
            public int payloadVersion() {
                return 1;
            }
        };
    }
}
