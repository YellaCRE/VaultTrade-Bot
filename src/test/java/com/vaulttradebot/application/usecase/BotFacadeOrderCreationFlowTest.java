package com.vaulttradebot.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.vaulttradebot.adapter.out.InMemoryOrderOutboxTransactionAdapter;
import com.vaulttradebot.adapter.out.InMemoryOrderRepository;
import com.vaulttradebot.adapter.out.InMemoryOutboxRepository;
import com.vaulttradebot.adapter.out.InMemoryTradingCycleLockAdapter;
import com.vaulttradebot.adapter.out.InMemoryTradingCycleSnapshotRepository;
import com.vaulttradebot.application.port.out.BotSettingsRepository;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.port.out.MarketDataPort;
import com.vaulttradebot.application.port.out.NotificationPort;
import com.vaulttradebot.application.port.out.PortfolioRepository;
import com.vaulttradebot.domain.common.vo.Candle;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Price;
import com.vaulttradebot.domain.common.vo.Side;
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
class BotFacadeOrderCreationFlowTest {
    private static final Instant NOW = Instant.parse("2026-02-15T10:00:00Z");
    private static final Market MARKET = Market.of("KRW-BTC");

    @Mock
    private BotSettingsRepository botSettingsRepository;
    @Mock
    private MarketDataPort marketDataPort;
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
    private InMemoryTradingCycleSnapshotRepository cycleSnapshotRepository;
    private BotFacadeService service;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
        outboxRepository = new InMemoryOutboxRepository();
        cycleSnapshotRepository = new InMemoryTradingCycleSnapshotRepository();

        service = new BotFacadeService(
                botSettingsRepository,
                marketDataPort,
                portfolioRepository,
                orderRepository,
                notificationPort,
                clockPort,
                orderDecisionService,
                riskEvaluationService,
                new InMemoryOrderOutboxTransactionAdapter(orderRepository, outboxRepository, cycleSnapshotRepository),
                outboxRepository,
                cycleSnapshotRepository,
                new InMemoryTradingCycleLockAdapter(),
                strategy
        );

        when(botSettingsRepository.load()).thenReturn(BotConfig.defaultConfig());
        when(clockPort.now()).thenReturn(NOW);
        when(marketDataPort.getLastPrice(eq(MARKET))).thenReturn(Money.krw(new BigDecimal("50000000")));
        when(marketDataPort.getRecentCandles(any(), any(), anyInt(), any())).thenReturn(List.of(
                new Candle(
                        NOW.minusSeconds(120),
                        Price.of(new BigDecimal("49900000"), com.vaulttradebot.domain.common.vo.Asset.krw()),
                        Price.of(new BigDecimal("50100000"), com.vaulttradebot.domain.common.vo.Asset.krw()),
                        Price.of(new BigDecimal("49800000"), com.vaulttradebot.domain.common.vo.Asset.krw()),
                        Price.of(new BigDecimal("50000000"), com.vaulttradebot.domain.common.vo.Asset.krw()),
                        BigDecimal.ONE
                )
        ));
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
    }

    @Test
    void savesSnapshotAndOutboxWhenDecisionIsPlace() {
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
        assertThat(outboxRepository.findAll()).hasSize(1);
        assertThat(cycleSnapshotRepository.findByCycleId(outboxRepository.findAll().getFirst().aggregateId())).isPresent();
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void savesSnapshotOnlyWhenDecisionIsHold() {
        when(orderDecisionService.decide(any(OrderDecisionContext.class)))
                .thenReturn(OrderActionDecision.hold("risk rejected"));

        service.start();
        CycleResult result = service.runCycle();

        assertThat(result.orderPlaced()).isFalse();
        assertThat(outboxRepository.findAll()).isEmpty();
    }

    @Test
    void rollsBackSnapshotWhenOutboxFails() {
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
        assertThat(outboxRepository.findAll()).isEmpty();
    }
}
