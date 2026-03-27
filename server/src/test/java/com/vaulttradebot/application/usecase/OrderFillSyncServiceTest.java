package com.vaulttradebot.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaulttradebot.adapter.out.InMemoryOrderOutboxTransactionAdapter;
import com.vaulttradebot.adapter.out.InMemoryOrderRepository;
import com.vaulttradebot.adapter.out.InMemoryOutboxRepository;
import com.vaulttradebot.adapter.out.InMemoryPortfolioRepository;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.port.out.ExchangeTradingPort;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderFillSyncServiceTest {

    @Test
    void syncActiveOrdersPersistsNewFillDeltaIntoPortfolio() {
        // Verifies active-order sync persists only the newly filled quantity and fee into both order and portfolio state.
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        InMemoryOutboxRepository outboxRepository = new InMemoryOutboxRepository();
        InMemoryPortfolioRepository portfolioRepository = new InMemoryPortfolioRepository();
        ClockPort clock = () -> Instant.parse("2026-03-27T12:00:00Z");
        OrderPersistenceService orderPersistenceService = new OrderPersistenceService(
                orderRepository,
                outboxRepository,
                portfolioRepository,
                new InMemoryOrderOutboxTransactionAdapter(orderRepository, outboxRepository, portfolioRepository),
                clock,
                serializer()
        );

        Order order = Order.create(
                Market.of("KRW-BTC"),
                Side.BUY,
                new BigDecimal("0.01000000"),
                Money.krw(new BigDecimal("50000000")),
                clock.now()
        );
        order.acceptByExchange();
        order.bindExchangeOrderId("upbit-uuid-1");
        order.execute(new ExecutionTrade(
                "trade-1",
                Money.krw(new BigDecimal("50000000")),
                Quantity.of(new BigDecimal("0.00400000")),
                Money.krw(new BigDecimal("200")),
                clock.now()
        ));
        orderPersistenceService.persist(order);

        ExchangeTradingPort exchangeTradingPort = new ExchangeTradingPort() {
            @Override
            public Order placeOrder(Order current) {
                return current;
            }

            @Override
            public Order refreshOrder(Order current) {
                current.execute(new ExecutionTrade(
                        UUID.randomUUID().toString(),
                        Money.krw(new BigDecimal("50500000")),
                        Quantity.of(new BigDecimal("0.00600000")),
                        Money.krw(new BigDecimal("300")),
                        Instant.parse("2026-03-27T12:01:00Z")
                ));
                return current;
            }

            @Override
            public void cancelOrder(String orderId) {
            }
        };

        OrderFillSyncService service = new OrderFillSyncService(
                orderRepository,
                exchangeTradingPort,
                orderPersistenceService
        );

        service.syncActiveOrders();

        Order synced = orderRepository.findById(order.id()).orElseThrow();
        assertThat(synced.status().name()).isEqualTo("FILLED");
        assertThat(synced.executedQuantity().value()).isEqualByComparingTo("0.01000000");
        assertThat(synced.executedAmount().amount()).isEqualByComparingTo("503000");
        assertThat(synced.executedFee().amount()).isEqualByComparingTo("500");
        assertThat(portfolioRepository.findByMarket("KRW-BTC")).isPresent();
        assertThat(portfolioRepository.findByMarket("KRW-BTC").orElseThrow().quantity())
                .isEqualByComparingTo("0.01000000");
        assertThat(portfolioRepository.findByMarket("KRW-BTC").orElseThrow().averageEntryPrice().amount())
                .isEqualByComparingTo("50350000.00000000");
    }

    @Test
    void syncActiveOrdersSkipsOrdersWithoutExchangeOrderId() {
        // Verifies sync ignores active orders that cannot be refreshed because no exchange order id is bound yet.
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        InMemoryOutboxRepository outboxRepository = new InMemoryOutboxRepository();
        InMemoryPortfolioRepository portfolioRepository = new InMemoryPortfolioRepository();
        ClockPort clock = () -> Instant.parse("2026-03-27T12:00:00Z");
        OrderPersistenceService orderPersistenceService = new OrderPersistenceService(
                orderRepository,
                outboxRepository,
                portfolioRepository,
                new InMemoryOrderOutboxTransactionAdapter(orderRepository, outboxRepository, portfolioRepository),
                clock,
                serializer()
        );

        Order order = Order.create(
                Market.of("KRW-BTC"),
                Side.BUY,
                new BigDecimal("0.01000000"),
                Money.krw(new BigDecimal("50000000")),
                clock.now()
        );
        order.acceptByExchange();
        orderRepository.save(order);

        ExchangeTradingPort exchangeTradingPort = new ExchangeTradingPort() {
            @Override
            public Order placeOrder(Order current) {
                return current;
            }

            @Override
            public Order refreshOrder(Order current) {
                throw new AssertionError("refreshOrder should not be called when exchangeOrderId is missing");
            }

            @Override
            public void cancelOrder(String orderId) {
            }
        };

        OrderFillSyncService service = new OrderFillSyncService(
                orderRepository,
                exchangeTradingPort,
                orderPersistenceService
        );

        service.syncActiveOrders();

        assertThat(orderRepository.findById(order.id())).isPresent();
        assertThat(outboxRepository.findAll()).isEmpty();
        assertThat(portfolioRepository.findAll()).isEmpty();
    }

    private OutboxPayloadSerializer serializer() {
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
