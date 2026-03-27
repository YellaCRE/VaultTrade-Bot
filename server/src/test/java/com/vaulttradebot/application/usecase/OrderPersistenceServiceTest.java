package com.vaulttradebot.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaulttradebot.adapter.out.InMemoryOrderOutboxTransactionAdapter;
import com.vaulttradebot.adapter.out.InMemoryOrderRepository;
import com.vaulttradebot.adapter.out.InMemoryOutboxRepository;
import com.vaulttradebot.adapter.out.InMemoryPortfolioRepository;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.port.out.OutboxPayloadSerializer;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Quantity;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.domain.execution.event.OrderDomainEvent;
import com.vaulttradebot.domain.execution.vo.ExecutionTrade;
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
        InMemoryPortfolioRepository portfolioRepository = new InMemoryPortfolioRepository();
        ClockPort clock = () -> Instant.parse("2026-02-15T10:00:00Z");
        OrderPersistenceService service = new OrderPersistenceService(
                orderRepository,
                outboxRepository,
                portfolioRepository,
                new InMemoryOrderOutboxTransactionAdapter(orderRepository, outboxRepository, portfolioRepository),
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
        assertThat(portfolioRepository.findAll()).isEmpty();
        assertThat(outboxRepository.findAll().getFirst().aggregateId()).isEqualTo(order.id());
        assertThat(order.pullDomainEvents()).isEmpty();
    }

    @Test
    void rollsBackOrderWriteWhenOutboxSaveFails() {
        // Verifies outbox failure rolls back order persistence and restores domain events.
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        InMemoryOutboxRepository outboxRepository = new InMemoryOutboxRepository();
        InMemoryPortfolioRepository portfolioRepository = new InMemoryPortfolioRepository();
        outboxRepository.setFailOnSave(true);

        ClockPort clock = () -> Instant.parse("2026-02-15T10:00:00Z");
        OrderPersistenceService service = new OrderPersistenceService(
                orderRepository,
                outboxRepository,
                portfolioRepository,
                new InMemoryOrderOutboxTransactionAdapter(orderRepository, outboxRepository, portfolioRepository),
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
        assertThat(portfolioRepository.findAll()).isEmpty();

        List<OrderDomainEvent> restored = order.pullDomainEvents();
        assertThat(restored).hasSize(1);
        assertThat(restored.getFirst().orderId()).isEqualTo(order.id());
    }

    @Test
    void updatesPortfolioWhenFilledBuyOrderIsPersisted() {
        // Verifies a filled buy order writes the resulting position in the same transaction including fee-adjusted cost basis.
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        InMemoryOutboxRepository outboxRepository = new InMemoryOutboxRepository();
        InMemoryPortfolioRepository portfolioRepository = new InMemoryPortfolioRepository();
        ClockPort clock = () -> Instant.parse("2026-02-15T10:00:00Z");
        OrderPersistenceService service = new OrderPersistenceService(
                orderRepository,
                outboxRepository,
                portfolioRepository,
                new InMemoryOrderOutboxTransactionAdapter(orderRepository, outboxRepository, portfolioRepository),
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
        order.acceptByExchange();
        order.execute(new ExecutionTrade(
                "trade-1",
                Money.krw(new BigDecimal("50000000")),
                Quantity.of(new BigDecimal("0.00200000")),
                Money.krw(new BigDecimal("100")),
                clock.now()
        ));

        service.persist(order);

        assertThat(portfolioRepository.findByMarket("KRW-BTC")).isPresent();
        assertThat(portfolioRepository.findByMarket("KRW-BTC").orElseThrow().quantity())
                .isEqualByComparingTo("0.00200000");
        assertThat(portfolioRepository.findByMarket("KRW-BTC").orElseThrow().averageEntryPrice().amount())
                .isEqualByComparingTo("50050000.00000000");
    }

    @Test
    void rollsBackPortfolioWriteWhenOutboxSaveFailsAfterFill() {
        // Verifies filled-order position updates are rolled back together with order/outbox writes.
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        InMemoryOutboxRepository outboxRepository = new InMemoryOutboxRepository();
        InMemoryPortfolioRepository portfolioRepository = new InMemoryPortfolioRepository();
        outboxRepository.setFailOnSave(true);
        ClockPort clock = () -> Instant.parse("2026-02-15T10:00:00Z");
        OrderPersistenceService service = new OrderPersistenceService(
                orderRepository,
                outboxRepository,
                portfolioRepository,
                new InMemoryOrderOutboxTransactionAdapter(orderRepository, outboxRepository, portfolioRepository),
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
        order.acceptByExchange();
        order.execute(new ExecutionTrade(
                "trade-1",
                Money.krw(new BigDecimal("50000000")),
                Quantity.of(new BigDecimal("0.00200000")),
                Money.krw(BigDecimal.ZERO),
                clock.now()
        ));

        assertThatThrownBy(() -> service.persist(order))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forced outbox failure");

        assertThat(portfolioRepository.findAll()).isEmpty();
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
