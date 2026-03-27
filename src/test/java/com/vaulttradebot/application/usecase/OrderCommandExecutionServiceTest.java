package com.vaulttradebot.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaulttradebot.adapter.out.InMemoryOrderOutboxTransactionAdapter;
import com.vaulttradebot.adapter.out.InMemoryOrderRepository;
import com.vaulttradebot.adapter.out.InMemoryOutboxRepository;
import com.vaulttradebot.adapter.out.InMemoryPortfolioRepository;
import com.vaulttradebot.adapter.out.PaperExchangeTradingAdapter;
import com.vaulttradebot.application.outbox.OutboxMessage;
import com.vaulttradebot.application.port.in.BotControlUseCase;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.port.out.ExchangeTradingPort;
import com.vaulttradebot.application.port.out.OutboxPayloadSerializer;
import com.vaulttradebot.application.query.BotStatusSnapshot;
import com.vaulttradebot.config.ApiTimeSupport;
import com.vaulttradebot.config.VaultTradingProperties;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.domain.execution.event.OrderDomainEvent;
import com.vaulttradebot.domain.ops.BotRunState;
import com.vaulttradebot.domain.ops.KillSwitchActiveException;
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
                inactiveKillSwitch(),
                new PaperExchangeTradingAdapter(clock, tradingProperties()),
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
                inactiveKillSwitch(),
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

    @Test
    void blocksCreateCommandsWhileKillSwitchIsActive() {
        // Verifies kill switch protection blocks CREATE commands before they reach the exchange adapter.
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
                activeKillSwitch("manual emergency stop", clock.now()),
                new PaperExchangeTradingAdapter(clock, tradingProperties()),
                orderRepository,
                orderPersistenceService,
                new ObjectMapper()
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                KillSwitchActiveException.class,
                () -> service.execute(message(clock.now()))
        );
        assertThat(orderRepository.findAll()).isEmpty();
    }

    private BotControlUseCase inactiveKillSwitch() {
        return new BotControlUseCase() {
            @Override
            public BotStatusSnapshot status() {
                return new BotStatusSnapshot(BotRunState.STOPPED, null, null, 0, null, null);
            }

            @Override
            public BotStatusSnapshot start() {
                throw new UnsupportedOperationException();
            }

            @Override
            public BotStatusSnapshot stop() {
                throw new UnsupportedOperationException();
            }

            @Override
            public BotStatusSnapshot activateKillSwitch(String reason, boolean cancelActiveOrders) {
                throw new UnsupportedOperationException();
            }

            @Override
            public BotStatusSnapshot releaseKillSwitch() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isKillSwitchActive() {
                return false;
            }
        };
    }

    private BotControlUseCase activeKillSwitch(String reason, Instant activatedAt) {
        return new BotControlUseCase() {
            @Override
            public BotStatusSnapshot status() {
                return new BotStatusSnapshot(
                        BotRunState.EMERGENCY_STOP,
                        ApiTimeSupport.toApiTime(activatedAt),
                        "kill-switch: " + reason,
                        0,
                        ApiTimeSupport.toApiTime(activatedAt),
                        reason
                );
            }

            @Override
            public BotStatusSnapshot start() {
                throw new UnsupportedOperationException();
            }

            @Override
            public BotStatusSnapshot stop() {
                throw new UnsupportedOperationException();
            }

            @Override
            public BotStatusSnapshot activateKillSwitch(String reason, boolean cancelActiveOrders) {
                throw new UnsupportedOperationException();
            }

            @Override
            public BotStatusSnapshot releaseKillSwitch() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isKillSwitchActive() {
                return true;
            }
        };
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

    private VaultTradingProperties tradingProperties() {
        VaultTradingProperties properties = new VaultTradingProperties();
        properties.getPaper().setFillOnPlace(true);
        return properties;
    }
}
