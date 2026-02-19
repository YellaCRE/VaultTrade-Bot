package com.vaulttradebot.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaulttradebot.adapter.out.InMemoryTradingCycleLockAdapter;
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
import com.vaulttradebot.domain.common.vo.Asset;
import com.vaulttradebot.domain.common.vo.Candle;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Price;
import com.vaulttradebot.domain.common.vo.Timeframe;
import com.vaulttradebot.domain.ops.BotConfig;
import com.vaulttradebot.domain.portfolio.Position;
import com.vaulttradebot.domain.risk.RiskDecision;
import com.vaulttradebot.domain.risk.RiskEvaluationService;
import com.vaulttradebot.domain.trading.OrderDecisionService;
import com.vaulttradebot.domain.trading.model.sizing.QuantityCalculator;
import com.vaulttradebot.domain.trading.model.strategy.Strategy;
import com.vaulttradebot.domain.trading.model.strategy.vo.SignalDecision;
import com.vaulttradebot.domain.trading.vo.OrderDecisionType;
import com.vaulttradebot.domain.trading.vo.SignalAction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BotFacadeMarketDataPortMockTest {
    private static final Instant NOW = Instant.parse("2026-02-15T10:00:00Z");
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

    private final OrderDecisionService orderDecisionService = new OrderDecisionService(new QuantityCalculator());

    @BeforeEach
    void setUp() {
        when(botSettingsRepository.load()).thenReturn(BotConfig.defaultConfig());
        when(clockPort.now()).thenReturn(NOW);
        when(portfolioRepository.findByMarket("KRW-BTC")).thenReturn(Optional.of(Position.open(MARKET, NOW.minusSeconds(60))));
        when(orderRepository.findAll()).thenReturn(List.of());
        when(tradingCycleLockPort.tryAcquire(any())).thenReturn(true);
        when(tradingCycleSnapshotRepository.findByCycleId(any())).thenReturn(Optional.empty());
        when(strategy.evaluate(any())).thenReturn(new SignalDecision(
                SignalAction.BUY,
                0.9,
                "mock-signal",
                NOW,
                "KRW-BTC",
                Timeframe.M1
        ));
        when(riskEvaluationService.approveAndReserve(any())).thenReturn(
                RiskDecision.allow("RISK_OK", "risk passed", new BigDecimal("100000"), Map.of())
        );
        // Execute transaction callback immediately to keep unit tests deterministic.
        org.mockito.Mockito.doAnswer(invocation -> {
            Runnable action = invocation.getArgument(0);
            action.run();
            return null;
        }).when(orderOutboxTransactionPort).execute(any(Runnable.class));
    }

    @Test
    void usesSingleMarketSnapshotPerCycleAndRequestsOrderOnFreshData() {
        // Fresh market data should produce one deterministic decision and one market snapshot call.
        when(marketDataPort.getRecentCandles(any(), any(), anyInt(), any())).thenReturn(
                List.of(candleAt(NOW.minusSeconds(61), "49900000", "50100000", "49800000", "50000000"))
        );
        when(marketDataPort.getLastPrice(eq(MARKET))).thenReturn(Money.krw(new BigDecimal("50000000")));

        BotFacadeService service = newService(orderDecisionService, tradingCycleLockPort);
        service.start();
        CycleResult result = service.runCycle();

        assertThat(result.executed()).isTrue();
        assertThat(result.orderPlaced()).isTrue();
        verify(marketDataPort, times(1)).getRecentCandles(eq(MARKET), eq(Timeframe.M1), eq(150), eq(NOW));
        verify(marketDataPort, times(1)).getLastPrice(eq(MARKET));
    }

    @Test
    void rejectsStaleMarketDataAndKeepsDecisionAsHold() {
        // A stale candle timestamp should force HOLD and skip outbox command creation.
        when(marketDataPort.getRecentCandles(any(), any(), anyInt(), any())).thenReturn(
                List.of(candleAt(NOW.minusSeconds(120), "49900000", "50100000", "49800000", "50000000"))
        );
        when(marketDataPort.getLastPrice(eq(MARKET))).thenReturn(Money.krw(new BigDecimal("50000000")));

        BotFacadeService service = newService(orderDecisionService, tradingCycleLockPort);
        service.start();
        CycleResult result = service.runCycle();

        ArgumentCaptor<TradingCycleSnapshot> snapshotCaptor = ArgumentCaptor.forClass(TradingCycleSnapshot.class);
        verify(tradingCycleSnapshotRepository, times(1)).save(snapshotCaptor.capture());
        TradingCycleSnapshot snapshot = snapshotCaptor.getValue();

        assertThat(result.executed()).isTrue();
        assertThat(result.orderPlaced()).isFalse();
        assertThat(snapshot.decisionType()).isEqualTo(OrderDecisionType.HOLD);
        assertThat(snapshot.decisionReason()).isEqualTo("market data is stale");
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void skipsCycleWhenNoClosedCandleExistsAtTimeBoundary() {
        // This candle closes after NOW, so it must not be used for decision timestamp.
        when(marketDataPort.getRecentCandles(any(), any(), anyInt(), any())).thenReturn(
                List.of(candleAt(NOW.minusSeconds(30), "49900000", "50100000", "49800000", "50000000"))
        );

        BotFacadeService service = newService(orderDecisionService, tradingCycleLockPort);
        service.start();
        CycleResult result = service.runCycle();

        assertThat(result.executed()).isFalse();
        assertThat(result.orderPlaced()).isFalse();
        assertThat(result.message()).isEqualTo("cycle skipped: no closed candle");
        verify(marketDataPort, never()).getLastPrice(any());
        verify(strategy, never()).evaluate(any());
    }

    @Test
    void propagatesMarketDataFailureAsSafeSkipWithoutDownstreamCalls() {
        // Market data fetch errors must be contained as a safe cycle skip.
        when(marketDataPort.getRecentCandles(any(), any(), anyInt(), any()))
                .thenThrow(new IllegalStateException("rate limited"));

        BotFacadeService service = newService(orderDecisionService, tradingCycleLockPort);
        service.start();
        CycleResult result = service.runCycle();

        assertThat(result.executed()).isFalse();
        assertThat(result.orderPlaced()).isFalse();
        assertThat(result.message()).isEqualTo("cycle skipped: market data unavailable");
        verify(marketDataPort, never()).getLastPrice(any());
        verify(strategy, never()).evaluate(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void preventsMarketDataCallAmplificationUnderConcurrencyWithLock() throws Exception {
        // Concurrent cycle requests should be collapsed by lock to avoid data-port call amplification.
        InMemoryTradingCycleLockAdapter lockAdapter = new InMemoryTradingCycleLockAdapter();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        when(marketDataPort.getRecentCandles(any(), any(), anyInt(), any())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(2, TimeUnit.SECONDS);
            return List.of(candleAt(NOW.minusSeconds(61), "49900000", "50100000", "49800000", "50000000"));
        });
        when(marketDataPort.getLastPrice(eq(MARKET))).thenReturn(Money.krw(new BigDecimal("50000000")));

        BotFacadeService service = newService(orderDecisionService, lockAdapter);
        service.start();

        int requestCount = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        List<Future<CycleResult>> futures = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            futures.add(executor.submit(() -> {
                start.await(2, TimeUnit.SECONDS);
                return service.runCycle();
            }));
        }

        start.countDown();
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        release.countDown();

        int lockSkipped = 0;
        for (Future<CycleResult> future : futures) {
            CycleResult result = future.get(3, TimeUnit.SECONDS);
            if ("cycle skipped: lock not acquired".equals(result.message())) {
                lockSkipped++;
            }
        }
        executor.shutdownNow();

        assertThat(lockSkipped).isGreaterThanOrEqualTo(1);
        verify(marketDataPort, times(1)).getRecentCandles(eq(MARKET), eq(Timeframe.M1), eq(150), eq(NOW));
    }

    private BotFacadeService newService(OrderDecisionService realOrderDecisionService, TradingCycleLockPort lockPort) {
        return new BotFacadeService(
                botSettingsRepository,
                marketDataPort,
                portfolioRepository,
                orderRepository,
                notificationPort,
                clockPort,
                realOrderDecisionService,
                riskEvaluationService,
                orderOutboxTransactionPort,
                outboxRepository,
                tradingCycleSnapshotRepository,
                lockPort,
                strategy
        );
    }

    private Candle candleAt(Instant openTime, String open, String high, String low, String close) {
        return new Candle(
                openTime,
                Price.of(new BigDecimal(open), Asset.krw()),
                Price.of(new BigDecimal(high), Asset.krw()),
                Price.of(new BigDecimal(low), Asset.krw()),
                Price.of(new BigDecimal(close), Asset.krw()),
                BigDecimal.ONE
        );
    }
}
