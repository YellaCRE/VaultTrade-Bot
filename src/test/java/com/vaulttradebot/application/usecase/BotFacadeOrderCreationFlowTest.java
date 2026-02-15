package com.vaulttradebot.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaulttradebot.adapter.out.InMemoryIdempotencyRepository;
import com.vaulttradebot.adapter.out.InMemoryOrderOutboxTransactionAdapter;
import com.vaulttradebot.adapter.out.InMemoryOrderRepository;
import com.vaulttradebot.adapter.out.InMemoryOutboxRepository;
import com.vaulttradebot.application.idempotency.IdempotentOrderCommandService;
import com.vaulttradebot.application.port.out.BotSettingsRepository;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.port.out.ExchangeTradingPort;
import com.vaulttradebot.application.port.out.MarketDataPort;
import com.vaulttradebot.application.port.out.NotificationPort;
import com.vaulttradebot.application.port.out.OutboxPayloadSerializer;
import com.vaulttradebot.application.port.out.PortfolioRepository;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.common.vo.Timeframe;
import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.domain.execution.event.OrderDomainEvent;
import com.vaulttradebot.domain.execution.vo.OrderStatus;
import com.vaulttradebot.domain.ops.BotConfig;
import com.vaulttradebot.domain.portfolio.Position;
import com.vaulttradebot.domain.risk.RiskEvaluationService;
import com.vaulttradebot.domain.trading.OrderActionDecision;
import com.vaulttradebot.domain.trading.OrderCommand;
import com.vaulttradebot.domain.trading.OrderDecisionService;
import com.vaulttradebot.domain.trading.model.strategy.Strategy;
import com.vaulttradebot.domain.trading.model.strategy.vo.SignalDecision;
import com.vaulttradebot.domain.trading.vo.OrderDecisionContext;
import com.vaulttradebot.domain.trading.vo.SignalAction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BotFacadeOrderCreationFlowTest {
    private static final Instant NOW = Instant.parse("2026-02-15T10:00:00Z");
    private static final Market MARKET = Market.of("KRW-BTC");

    @Mock
    private BotSettingsRepository botSettingsRepository;
    @Mock
    private MarketDataPort marketDataPort;
    @Mock
    private ExchangeTradingPort exchangeTradingPort;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private NotificationPort notificationPort;
    @Mock
    private ClockPort clockPort;
    @Mock
    private OrderDecisionService orderDecisionService;
    @Mock
    private RiskEvaluationService riskEvaluationService;
    @Mock
    private Strategy strategy;

    private InMemoryOrderRepository orderRepository;
    private InMemoryOutboxRepository outboxRepository;
    private BotFacadeService service;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
        outboxRepository = new InMemoryOutboxRepository();
        OrderPersistenceService orderPersistenceService = new OrderPersistenceService(
                orderRepository,
                outboxRepository,
                new InMemoryOrderOutboxTransactionAdapter(orderRepository, outboxRepository),
                clockPort,
                new OutboxPayloadSerializer() {
                    @Override
                    public String serialize(OrderDomainEvent event) {
                        return "{\"eventType\":\"" + event.getClass().getSimpleName() + "\"}";
                    }

                    @Override
                    public int payloadVersion() {
                        return 1;
                    }
                }
        );
        service = new BotFacadeService(
                botSettingsRepository,
                marketDataPort,
                exchangeTradingPort,
                portfolioRepository,
                orderRepository,
                notificationPort,
                clockPort,
                orderDecisionService,
                riskEvaluationService,
                new IdempotentOrderCommandService(new InMemoryIdempotencyRepository()),
                orderPersistenceService,
                strategy
        );

        when(botSettingsRepository.load()).thenReturn(BotConfig.defaultConfig());
        when(clockPort.now()).thenReturn(NOW);
        when(marketDataPort.getLastPrice(eq(MARKET))).thenReturn(Money.krw(new BigDecimal("50000000")));
        when(marketDataPort.getRecentCandles(any(), any(), anyInt(), any())).thenReturn(List.of());
        when(portfolioRepository.findByMarket("KRW-BTC")).thenReturn(Optional.of(Position.open(MARKET, NOW.minusSeconds(60))));
        when(strategy.evaluate(any())).thenReturn(new SignalDecision(
                SignalAction.BUY,
                0.9,
                "test-signal",
                NOW,
                "KRW-BTC",
                Timeframe.M1
        ));
        when(orderDecisionService.decide(any(SignalDecision.class), any(Market.class), any(Money.class), any(BigDecimal.class)))
                .thenReturn(Optional.empty());
        when(exchangeTradingPort.placeOrder(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void savesCreatedOrderWhenDecisionIsPlace() {
        // Verifies application flow creates and persists an order after PLACE decision.
        when(orderDecisionService.decide(any(OrderDecisionContext.class)))
                .thenReturn(OrderActionDecision.place(
                        OrderCommand.create(
                                MARKET,
                                Side.BUY,
                                Money.krw(new BigDecimal("50000000")),
                                new BigDecimal("0.00200000"),
                                "flow-place-1",
                                "new signal"
                        ),
                        "place new order"
                ));

        service.start();
        CycleResult result = service.runCycle();

        assertThat(result.orderPlaced()).isTrue();
        assertThat(orderRepository.findAll()).hasSize(1);
        assertThat(outboxRepository.findAll()).hasSize(1);

        Order saved = orderRepository.findAll().getFirst();
        assertThat(saved.market()).isEqualTo(MARKET);
        assertThat(saved.side()).isEqualTo(Side.BUY);
        assertThat(saved.price().amount()).isEqualByComparingTo("50000000");
        assertThat(saved.quantity()).isEqualByComparingTo("0.00200000");
        assertThat(saved.createdAt()).isEqualTo(NOW);
        assertThat(saved.status()).isEqualTo(OrderStatus.NEW);
    }

    @Test
    void doesNotSaveOrderWhenDecisionIsHold() {
        // Verifies rejected/hold flow does not persist any order and skips exchange call.
        when(orderDecisionService.decide(any(OrderDecisionContext.class)))
                .thenReturn(OrderActionDecision.hold("risk rejected"));

        service.start();
        CycleResult result = service.runCycle();

        assertThat(result.orderPlaced()).isFalse();
        assertThat(orderRepository.findAll()).isEmpty();
        assertThat(outboxRepository.findAll()).isEmpty();
        verify(exchangeTradingPort, never()).placeOrder(any(Order.class));
    }

    @Test
    void rollsBackPersistenceWhenOutboxFails() {
        // Verifies outbox write failure rolls back persisted order state in the same transaction.
        when(orderDecisionService.decide(any(OrderDecisionContext.class)))
                .thenReturn(OrderActionDecision.place(
                        OrderCommand.create(
                                MARKET,
                                Side.BUY,
                                Money.krw(new BigDecimal("50000000")),
                                new BigDecimal("0.00200000"),
                                "flow-place-fail-1",
                                "new signal"
                        ),
                        "place new order"
                ));
        outboxRepository.setFailOnSave(true);

        service.start();
        CycleResult result = service.runCycle();

        assertThat(result.orderPlaced()).isFalse();
        assertThat(result.message()).contains("forced outbox failure");
        assertThat(orderRepository.findAll()).isEmpty();
        assertThat(outboxRepository.findAll()).isEmpty();
        verify(exchangeTradingPort, times(1)).placeOrder(any(Order.class));
    }
}
