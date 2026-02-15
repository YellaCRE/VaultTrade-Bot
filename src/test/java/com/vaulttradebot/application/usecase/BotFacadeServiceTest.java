package com.vaulttradebot.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaulttradebot.application.port.out.BotSettingsRepository;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.port.out.ExchangeTradingPort;
import com.vaulttradebot.application.port.out.MarketDataPort;
import com.vaulttradebot.application.port.out.NotificationPort;
import com.vaulttradebot.application.port.out.OrderRepository;
import com.vaulttradebot.application.port.out.PortfolioRepository;
import com.vaulttradebot.domain.common.IdempotencyService;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Timeframe;
import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.domain.ops.BotConfig;
import com.vaulttradebot.domain.portfolio.Position;
import com.vaulttradebot.domain.risk.RiskEvaluationService;
import com.vaulttradebot.domain.trading.OrderActionDecision;
import com.vaulttradebot.domain.trading.OrderCommand;
import com.vaulttradebot.domain.trading.vo.OrderDecisionContext;
import com.vaulttradebot.domain.trading.OrderDecisionService;
import com.vaulttradebot.domain.trading.strategy.Strategy;
import com.vaulttradebot.domain.trading.strategy.vo.SignalDecision;
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

    private final IdempotencyService idempotencyService = new IdempotencyService();
    private BotFacadeService service;

    @BeforeEach
    void setUp() {
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
                idempotencyService,
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
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void placeDecisionCallsPlaceOnlyOnce() {
        // PLACE should submit one order and skip cancel.
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
        verify(exchangeTradingPort, times(1)).placeOrder(any(Order.class));
        verify(exchangeTradingPort, never()).cancelOrder(any());
    }

    @Test
    void modifyDecisionCallsCancelAndPlaceOnceEach() {
        // MODIFY should cancel old order then place replacement.
        Order openOrder = Order.create(
                MARKET,
                com.vaulttradebot.domain.common.vo.Side.BUY,
                new BigDecimal("0.00100000"),
                Money.krw(new BigDecimal("49900000")),
                NOW.minusSeconds(30)
        );
        when(orderRepository.findAll()).thenReturn(List.of(openOrder));
        when(orderDecisionService.decide(any(OrderDecisionContext.class)))
                .thenReturn(OrderActionDecision.modify(
                        OrderCommand.replace(
                                openOrder.id(),
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
        verify(exchangeTradingPort, times(1)).cancelOrder(openOrder.id());
        verify(exchangeTradingPort, times(1)).placeOrder(any(Order.class));
    }

    @Test
    void cancelDecisionCallsCancelOnlyOnce() {
        // CANCEL should call cancel only and never place.
        Order openOrder = Order.create(
                MARKET,
                com.vaulttradebot.domain.common.vo.Side.BUY,
                new BigDecimal("0.00100000"),
                Money.krw(new BigDecimal("49900000")),
                NOW.minusSeconds(30)
        );
        when(orderRepository.findAll()).thenReturn(List.of(openOrder));
        when(orderDecisionService.decide(any(OrderDecisionContext.class)))
                .thenReturn(OrderActionDecision.cancel(
                        OrderCommand.cancel(openOrder.id(), "risk rejected"),
                        "cancel open order"
                ));

        service.start();
        CycleResult result = service.runCycle();

        assertThat(result.orderPlaced()).isFalse();
        verify(exchangeTradingPort, times(1)).cancelOrder(openOrder.id());
        verify(exchangeTradingPort, never()).placeOrder(any(Order.class));
    }

    @Test
    void holdDecisionCallsNeitherPlaceNorCancel() {
        // HOLD should not touch exchange ports.
        when(orderDecisionService.decide(any(OrderDecisionContext.class)))
                .thenReturn(OrderActionDecision.hold("no action"));

        service.start();
        CycleResult result = service.runCycle();

        assertThat(result.orderPlaced()).isFalse();
        verify(exchangeTradingPort, never()).placeOrder(any(Order.class));
        verify(exchangeTradingPort, never()).cancelOrder(any());
    }
}
