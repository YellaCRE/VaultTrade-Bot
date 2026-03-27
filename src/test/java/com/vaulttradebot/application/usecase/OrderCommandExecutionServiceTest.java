package com.vaulttradebot.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaulttradebot.adapter.out.InMemoryOrderOutboxTransactionAdapter;
import com.vaulttradebot.adapter.out.InMemoryOrderRepository;
import com.vaulttradebot.adapter.out.InMemoryOutboxRepository;
import com.vaulttradebot.adapter.out.InMemoryPortfolioRepository;
import com.vaulttradebot.adapter.out.PaperExchangeTradingAdapter;
import com.vaulttradebot.application.outbox.OutboxMessage;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.port.out.ExchangeTradingPort;
import com.vaulttradebot.application.port.out.OutboxPayloadSerializer;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.domain.execution.event.OrderDomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OrderCommandExecutionServiceTest {

    @Test
    void executesOrderCommandRequestedAndPersistsFilledOrderAndPosition() {
        // Verifies outbox command execution reaches exchange and persists both order and portfolio state.
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
        OrderCommandExecutionService service = new OrderCommandExecutionService(
                new PaperExchangeTradingAdapter(),
                orderRepository,
                orderPersistenceService,
                new ObjectMapper()
        );

        service.execute(message(clock.now()));

        assertThat(orderRepository.findAll()).hasSize(1);
        assertThat(orderRepository.findAll().getFirst().status().name()).isEqualTo("FILLED");
        assertThat(portfolioRepository.findByMarket("KRW-BTC")).isPresent();
        assertThat(portfolioRepository.findByMarket("KRW-BTC").orElseThrow().quantity())
                .isEqualByComparingTo("0.00200000");
        assertThat(outboxRepository.findAll()).isNotEmpty();
    }

    @Test
    void cancelUsesExchangeOrderIdInsteadOfInternalOrderId() {
        // Verifies cancel looks up the aggregate first and forwards the exchange-native order id.
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
        AtomicReference<String> canceledExchangeOrderId = new AtomicReference<>();
        ExchangeTradingPort exchangeTradingPort = new ExchangeTradingPort() {
            @Override
            public Order placeOrder(Order order) {
                return order;
            }

            @Override
            public Order refreshOrder(Order order) {
                return order;
            }

            @Override
            public void cancelOrder(String orderId) {
                canceledExchangeOrderId.set(orderId);
            }
        };
        OrderCommandExecutionService service = new OrderCommandExecutionService(
                exchangeTradingPort,
                orderRepository,
                orderPersistenceService,
                new ObjectMapper()
        );

        Order existing = Order.create(
                Market.of("KRW-BTC"),
                Side.BUY,
                new BigDecimal("0.00200000"),
                Money.krw(new BigDecimal("50000000")),
                clock.now()
        );
        existing.acceptByExchange();
        existing.bindExchangeOrderId("upbit-uuid-1");
        orderRepository.save(existing);

        service.execute(cancelMessage(existing.id(), clock.now()));

        assertThat(canceledExchangeOrderId.get()).isEqualTo("upbit-uuid-1");
        assertThat(orderRepository.findById(existing.id())).isPresent();
        assertThat(orderRepository.findById(existing.id()).orElseThrow().status().name()).isEqualTo("CANCELED");
    }

    private OutboxMessage message(Instant now) {
        return new OutboxMessage(
                "msg-1",
                "TradingCycle",
                "cycle-1",
                "OrderCommandRequested",
                """
                {
                  "cycleId":"cycle-1",
                  "strategyId":"MovingAverageCrossStrategy",
                  "dataTimestamp":"2026-03-27T12:00:00Z",
                  "decision":"PLACE",
                  "reason":"signal confirmed",
                  "commandType":"CREATE",
                  "targetOrderId":"",
                  "market":"KRW-BTC",
                  "side":"BUY",
                  "orderType":"LIMIT",
                  "price":"50000000",
                  "quantity":"0.00200000",
                  "clientOrderId":"client-1"
                }
                """,
                1,
                now,
                now,
                null,
                0,
                null,
                now,
                null
        );
    }

    private OutboxMessage cancelMessage(String targetOrderId, Instant now) {
        return new OutboxMessage(
                "msg-cancel-1",
                "TradingCycle",
                "cycle-2",
                "OrderCommandRequested",
                """
                {
                  "cycleId":"cycle-2",
                  "strategyId":"MovingAverageCrossStrategy",
                  "dataTimestamp":"2026-03-27T12:00:00Z",
                  "decision":"CANCEL",
                  "reason":"signal changed",
                  "commandType":"CANCEL",
                  "targetOrderId":"%s",
                  "market":"",
                  "side":"",
                  "orderType":"",
                  "price":"",
                  "quantity":"",
                  "clientOrderId":""
                }
                """.formatted(targetOrderId),
                1,
                now,
                now,
                null,
                0,
                null,
                now,
                null
        );
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
