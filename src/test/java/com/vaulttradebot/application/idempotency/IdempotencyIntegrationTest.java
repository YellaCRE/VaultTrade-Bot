package com.vaulttradebot.application.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaulttradebot.adapter.out.InMemoryIdempotencyRepository;
import com.vaulttradebot.application.port.out.BotSettingsRepository;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.port.out.ExchangeTradingPort;
import com.vaulttradebot.application.port.out.MarketDataPort;
import com.vaulttradebot.application.port.out.NotificationPort;
import com.vaulttradebot.application.port.out.OrderRepository;
import com.vaulttradebot.application.port.out.PortfolioRepository;
import com.vaulttradebot.application.usecase.BotFacadeService;
import com.vaulttradebot.application.usecase.CycleResult;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.common.vo.Timeframe;
import com.vaulttradebot.domain.execution.Order;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotencyIntegrationTest {
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

    private BotFacadeService botFacadeService;
    private IdempotentOrderCommandService idempotentOrderCommandService;

    @BeforeEach
    void setUp() {
        // Use real in-memory idempotency store to verify claim/replay behavior.
        idempotentOrderCommandService = new IdempotentOrderCommandService(new InMemoryIdempotencyRepository());
        botFacadeService = new BotFacadeService(
                botSettingsRepository,
                marketDataPort,
                exchangeTradingPort,
                portfolioRepository,
                orderRepository,
                notificationPort,
                clockPort,
                orderDecisionService,
                riskEvaluationService,
                idempotentOrderCommandService,
                strategy
        );

        when(botSettingsRepository.load()).thenReturn(BotConfig.defaultConfig());
        when(clockPort.now()).thenReturn(NOW);
        when(marketDataPort.getLastPrice(eq(MARKET))).thenReturn(Money.krw(new BigDecimal("50000000")));
        when(marketDataPort.getRecentCandles(any(), any(), anyInt(), any())).thenReturn(List.of());
        when(portfolioRepository.findByMarket("KRW-BTC"))
                .thenReturn(Optional.of(Position.open(MARKET, NOW.minusSeconds(60))));
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
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void sameRequestTwiceProcessesOnlyOnce() {
        // Same key + same payload should execute once and replay on retry.
        when(orderDecisionService.decide(any(OrderDecisionContext.class)))
                .thenReturn(OrderActionDecision.place(
                        OrderCommand.create(
                                MARKET,
                                Side.BUY,
                                Money.krw(new BigDecimal("50000000")),
                                new BigDecimal("0.00200000"),
                                "idem-duplicate-1",
                                "new signal"
                        ),
                        "place new order"
                ));

        botFacadeService.start();
        CycleResult first = botFacadeService.runCycle();
        CycleResult second = botFacadeService.runCycle();

        // Both calls return successful order result, but exchange is called only once.
        assertThat(first.orderPlaced()).isTrue();
        assertThat(second.orderPlaced()).isTrue();
        verify(exchangeTradingPort, times(1)).placeOrder(any(Order.class));
    }

    @Test
    void sameKeyWithDifferentPayloadReturnsConflict() {
        // Same key with changed quantity should be rejected as a conflict.
        AtomicInteger calls = new AtomicInteger(0);
        when(orderDecisionService.decide(any(OrderDecisionContext.class)))
                .thenAnswer(invocation -> {
                    int n = calls.incrementAndGet();
                    BigDecimal qty = n == 1 ? new BigDecimal("0.00200000") : new BigDecimal("0.00300000");
                    return OrderActionDecision.place(
                            OrderCommand.create(
                                    MARKET,
                                    Side.BUY,
                                    Money.krw(new BigDecimal("50000000")),
                                    qty,
                                    "idem-conflict-1",
                                    "new signal"
                            ),
                            "place new order"
                    );
                });

        botFacadeService.start();
        CycleResult first = botFacadeService.runCycle();
        CycleResult second = botFacadeService.runCycle();

        // First request succeeds, second one is blocked by hash mismatch.
        assertThat(first.orderPlaced()).isTrue();
        assertThat(second.orderPlaced()).isFalse();
        assertThat(second.message()).contains("idempotency conflict");
        verify(exchangeTradingPort, times(1)).placeOrder(any(Order.class));
    }

    @Test
    void concurrentClaimsAllowOnlyOneWinner() throws Exception {
        // Two concurrent claim attempts for same key/hash: only one should win.
        String key = "idem-concurrent-1";
        String requestHash = IdempotencyHasher.sha256("same-payload");
        Instant now = NOW;
        Duration ttl = Duration.ofMinutes(10);
        CountDownLatch latch = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> attemptClaim = () -> {
                latch.await(1, TimeUnit.SECONDS);
                try {
                    Optional<CycleResult> replay = idempotentOrderCommandService.claimOrReplay(key, requestHash, now, ttl);
                    return replay.isEmpty();
                } catch (IllegalStateException inProgress) {
                    return false;
                }
            };

            Future<Boolean> first = executor.submit(attemptClaim);
            Future<Boolean> second = executor.submit(attemptClaim);
            latch.countDown();

            boolean firstClaimed = first.get(2, TimeUnit.SECONDS);
            boolean secondClaimed = second.get(2, TimeUnit.SECONDS);
            int claimedCount = (firstClaimed ? 1 : 0) + (secondClaimed ? 1 : 0);
            // Exactly one thread must own the key.
            assertThat(claimedCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void retryAfterLostResponseReusesFirstSnapshot() {
        // Simulate timeout after success, then retry with same request.
        when(orderDecisionService.decide(any(OrderDecisionContext.class)))
                .thenReturn(OrderActionDecision.place(
                        OrderCommand.create(
                                MARKET,
                                Side.BUY,
                                Money.krw(new BigDecimal("50000000")),
                                new BigDecimal("0.00200000"),
                                "idem-retry-1",
                                "new signal"
                        ),
                        "place new order"
                ));

        botFacadeService.start();
        CycleResult first = botFacadeService.runCycle();
        CycleResult retry = botFacadeService.runCycle();

        // Retry should replay the exact first response snapshot.
        assertThat(retry).isEqualTo(first);
        verify(exchangeTradingPort, times(1)).placeOrder(any(Order.class));
    }
}
