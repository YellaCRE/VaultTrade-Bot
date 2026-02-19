package com.vaulttradebot.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaulttradebot.application.outbox.OutboxMessage;
import com.vaulttradebot.application.port.out.BotSettingsRepository;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.port.out.MarketDataPort;
import com.vaulttradebot.application.port.out.NotificationPort;
import com.vaulttradebot.application.port.out.OrderOutboxTransactionPort;
import com.vaulttradebot.application.port.out.OrderRepository;
import com.vaulttradebot.application.port.out.OutboxRepository;
import com.vaulttradebot.application.port.out.PortfolioRepository;
import com.vaulttradebot.application.port.out.TradingCycleLockPort;
import com.vaulttradebot.application.port.out.TradingCycleSnapshotRepository;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Timeframe;
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
class BotFacadeServiceTest {
    private static final Instant NOW = Instant.parse("2026-02-15T10:00:00Z");
    private static final Instant CANDLE_OPEN = NOW.minusSeconds(120);
    private static final Market MARKET = Market.of("KRW-BTC");

    @Mock
    private BotSettingsRepository botSettingsRepository;
    @Mock
    private MarketDataPort marketDataPort;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private OrderRepository orderRepository;
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
    @Mock
    private OrderOutboxTransactionPort orderOutboxTransactionPort;
    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private TradingCycleSnapshotRepository tradingCycleSnapshotRepository;
    @Mock
    private TradingCycleLockPort tradingCycleLockPort;

    private BotFacadeService service;

    @BeforeEach
    void setUp() {
        service = new BotFacadeService(
                botSettingsRepository,
                marketDataPort,
                portfolioRepository,
                orderRepository,
                notificationPort,
                clockPort,
                orderDecisionService,
                riskEvaluationService,
                orderOutboxTransactionPort,
                outboxRepository,
                tradingCycleSnapshotRepository,
                tradingCycleLockPort,
                strategy
        );

        when(botSettingsRepository.load()).thenReturn(BotConfig.defaultConfig());
        when(clockPort.now()).thenReturn(NOW);
        when(marketDataPort.getLastPrice(eq(MARKET))).thenReturn(Money.krw(new BigDecimal("50000000")));
        when(marketDataPort.getRecentCandles(any(), any(), anyInt(), any())).thenReturn(List.of(
                new com.vaulttradebot.domain.common.vo.Candle(
                        CANDLE_OPEN,
                        com.vaulttradebot.domain.common.vo.Price.of(new BigDecimal("49900000"), com.vaulttradebot.domain.common.vo.Asset.krw()),
                        com.vaulttradebot.domain.common.vo.Price.of(new BigDecimal("50100000"), com.vaulttradebot.domain.common.vo.Asset.krw()),
                        com.vaulttradebot.domain.common.vo.Price.of(new BigDecimal("49800000"), com.vaulttradebot.domain.common.vo.Asset.krw()),
                        com.vaulttradebot.domain.common.vo.Price.of(new BigDecimal("50000000"), com.vaulttradebot.domain.common.vo.Asset.krw()),
                        new BigDecimal("1.0")
                )
        ));
        when(portfolioRepository.findByMarket("KRW-BTC")).thenReturn(Optional.of(Position.open(MARKET, NOW.minusSeconds(60))));
        when(strategy.evaluate(any())).thenReturn(new SignalDecision(
                SignalAction.BUY,
                0.9,
                "test-signal",
                NOW.minusSeconds(60),
                "KRW-BTC",
                Timeframe.M1
        ));
        when(orderDecisionService.decide(any(SignalDecision.class), any(Market.class), any(Money.class), any(BigDecimal.class)))
                .thenReturn(Optional.empty());
        when(orderRepository.findAll()).thenReturn(List.of());
        when(tradingCycleLockPort.tryAcquire(any())).thenReturn(true);
        when(tradingCycleSnapshotRepository.findByCycleId(any())).thenReturn(Optional.empty());
        // Execute transaction callback immediately in tests.
        org.mockito.Mockito.doAnswer(invocation -> {
            Runnable action = invocation.getArgument(0);
            action.run();
            return null;
        }).when(orderOutboxTransactionPort).execute(any(Runnable.class));
    }

    @Test
    void placeDecisionEnqueuesOutboxCommand() {
        when(orderDecisionService.decide(any(OrderDecisionContext.class)))
                .thenReturn(OrderActionDecision.place(
                        OrderCommand.create(
                                MARKET,
                                com.vaulttradebot.domain.common.vo.Side.BUY,
                                Money.krw(new BigDecimal("50000000")),
                                new BigDecimal("0.00200000"),
                                "client-place-1",
                                "new signal"
                        ),
                        "place new order"
                ));

        service.start();
        CycleResult result = service.runCycle();

        assertThat(result.orderPlaced()).isTrue();
        verify(outboxRepository, times(1)).save(any(OutboxMessage.class));
        verify(tradingCycleSnapshotRepository, times(1)).save(any(TradingCycleSnapshot.class));
    }

    @Test
    void modifyDecisionEnqueuesOneOutboxCommand() {
        when(orderDecisionService.decide(any(OrderDecisionContext.class)))
                .thenReturn(OrderActionDecision.modify(
                        OrderCommand.replace(
                                "order-1",
                                MARKET,
                                com.vaulttradebot.domain.common.vo.Side.BUY,
                                Money.krw(new BigDecimal("50000000")),
                                new BigDecimal("0.00200000"),
                                "client-modify-1",
                                "replace"
                        ),
                        "replace open order"
                ));

        service.start();
        CycleResult result = service.runCycle();

        assertThat(result.orderPlaced()).isTrue();
        verify(outboxRepository, times(1)).save(any(OutboxMessage.class));
    }

    @Test
    void cancelDecisionEnqueuesCancelCommand() {
        when(orderDecisionService.decide(any(OrderDecisionContext.class)))
                .thenReturn(OrderActionDecision.cancel(
                        OrderCommand.cancel("order-1", "risk rejected"),
                        "cancel open order"
                ));

        service.start();
        CycleResult result = service.runCycle();

        assertThat(result.orderPlaced()).isTrue();
        verify(outboxRepository, times(1)).save(any(OutboxMessage.class));
    }

    @Test
    void holdDecisionStoresSnapshotOnly() {
        when(orderDecisionService.decide(any(OrderDecisionContext.class)))
                .thenReturn(OrderActionDecision.hold("no action"));

        service.start();
        CycleResult result = service.runCycle();

        assertThat(result.orderPlaced()).isFalse();
        verify(outboxRepository, never()).save(any(OutboxMessage.class));
        verify(tradingCycleSnapshotRepository, times(1)).save(any(TradingCycleSnapshot.class));
    }
}
