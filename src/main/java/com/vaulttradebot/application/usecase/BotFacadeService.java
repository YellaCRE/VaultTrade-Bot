package com.vaulttradebot.application.usecase;

import com.vaulttradebot.application.port.in.BotConfigUseCase;
import com.vaulttradebot.application.port.in.BotControlUseCase;
import com.vaulttradebot.application.port.in.BotQueryUseCase;
import com.vaulttradebot.application.port.in.RunTradingCycleUseCase;
import com.vaulttradebot.application.port.out.BotSettingsRepository;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.port.out.ExchangeTradingPort;
import com.vaulttradebot.application.port.out.MarketDataPort;
import com.vaulttradebot.application.port.out.NotificationPort;
import com.vaulttradebot.application.port.out.OrderRepository;
import com.vaulttradebot.application.port.out.PortfolioRepository;
import com.vaulttradebot.application.query.BotStatusSnapshot;
import com.vaulttradebot.application.query.MetricsSnapshot;
import com.vaulttradebot.application.query.PortfolioSnapshot;
import com.vaulttradebot.domain.common.IdempotencyService;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.domain.execution.vo.OrderStatus;
import com.vaulttradebot.domain.ops.BotConfig;
import com.vaulttradebot.domain.ops.BotRunState;
import com.vaulttradebot.domain.portfolio.Position;
import com.vaulttradebot.domain.risk.snapshot.RiskAccountSnapshot;
import com.vaulttradebot.domain.risk.vo.RiskContext;
import com.vaulttradebot.domain.risk.RiskDecision;
import com.vaulttradebot.domain.risk.RiskEvaluationService;
import com.vaulttradebot.domain.risk.snapshot.RiskMarketSnapshot;
import com.vaulttradebot.domain.risk.snapshot.RiskMetricsSnapshot;
import com.vaulttradebot.domain.risk.RiskOrderRequest;
import com.vaulttradebot.domain.risk.RiskPolicy;
import com.vaulttradebot.domain.trading.snapshot.OpenOrderSnapshot;
import com.vaulttradebot.domain.trading.OrderActionDecision;
import com.vaulttradebot.domain.trading.OrderCommand;
import com.vaulttradebot.domain.trading.vo.OrderDecisionContext;
import com.vaulttradebot.domain.trading.vo.OrderDecisionType;
import com.vaulttradebot.domain.trading.OrderMarketPolicy;
import com.vaulttradebot.domain.trading.vo.OrderDecision;
import com.vaulttradebot.domain.trading.OrderDecisionService;
import com.vaulttradebot.domain.trading.strategy.vo.SignalDecision;
import com.vaulttradebot.domain.trading.strategy.Strategy;
import com.vaulttradebot.domain.trading.strategy.vo.StrategyContext;
import com.vaulttradebot.domain.trading.strategy.snapshot.StrategyPositionSnapshot;
import com.vaulttradebot.domain.common.vo.Timeframe;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class BotFacadeService implements BotControlUseCase, BotConfigUseCase, RunTradingCycleUseCase, BotQueryUseCase {
    private static final int CIRCUIT_BREAKER_THRESHOLD = 3;
    private static final String DEFAULT_ACCOUNT_ID = "default-account";
    private static final Set<OrderStatus> ACTIVE_ORDER_STATUSES = Set.of(
            OrderStatus.NEW,
            OrderStatus.OPEN,
            OrderStatus.PARTIAL_FILLED,
            OrderStatus.CANCEL_REQUESTED
    );

    private final BotSettingsRepository botSettingsRepository;
    private final MarketDataPort marketDataPort;
    private final ExchangeTradingPort exchangeTradingPort;
    private final PortfolioRepository portfolioRepository;
    private final OrderRepository orderRepository;
    private final NotificationPort notificationPort;
    private final ClockPort clockPort;
    private final OrderDecisionService orderDecisionService;
    private final RiskEvaluationService riskEvaluationService;
    private final IdempotencyService idempotencyService;
    private final Strategy strategy;

    private final AtomicReference<BotRunState> state = new AtomicReference<>(BotRunState.STOPPED);
    private final AtomicReference<Instant> lastCycleAt = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final AtomicReference<Instant> lastOrderAt = new AtomicReference<>();
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong successfulCycles = new AtomicLong(0);
    private final AtomicLong failedCycles = new AtomicLong(0);

    public BotFacadeService(
            BotSettingsRepository botSettingsRepository,
            MarketDataPort marketDataPort,
            ExchangeTradingPort exchangeTradingPort,
            PortfolioRepository portfolioRepository,
            OrderRepository orderRepository,
            NotificationPort notificationPort,
            ClockPort clockPort,
            OrderDecisionService orderDecisionService,
            RiskEvaluationService riskEvaluationService,
            IdempotencyService idempotencyService,
            Strategy strategy
    ) {
        this.botSettingsRepository = botSettingsRepository;
        this.marketDataPort = marketDataPort;
        this.exchangeTradingPort = exchangeTradingPort;
        this.portfolioRepository = portfolioRepository;
        this.orderRepository = orderRepository;
        this.notificationPort = notificationPort;
        this.clockPort = clockPort;
        this.orderDecisionService = orderDecisionService;
        this.riskEvaluationService = riskEvaluationService;
        this.idempotencyService = idempotencyService;
        this.strategy = strategy;
    }

    @Override
    public BotStatusSnapshot status() {
        return snapshot();
    }

    @Override
    public BotStatusSnapshot start() {
        if (state.get() != BotRunState.CIRCUIT_OPEN) {
            state.set(BotRunState.RUNNING);
            lastError.set(null);
        }
        return snapshot();
    }

    @Override
    public BotStatusSnapshot stop() {
        state.set(BotRunState.STOPPED);
        return snapshot();
    }

    @Override
    public BotConfig getConfig() {
        return botSettingsRepository.load();
    }

    @Override
    public BotConfig updateConfig(BotConfig config) {
        return botSettingsRepository.save(config);
    }

    @Override
    public synchronized CycleResult runCycle() {
        Instant now = clockPort.now();
        lastCycleAt.set(now);

        if (state.get() != BotRunState.RUNNING) {
            successfulCycles.incrementAndGet();
            return new CycleResult(false, false, "bot is not running");
        }

        String reservationId = null;
        try {
            BotConfig config = botSettingsRepository.load();
            Market market = toMarket(config.marketSymbol());
            Money lastPrice = marketDataPort.getLastPrice(market);
            Optional<OpenOrderSnapshot> openOrder = findLatestOpenOrder(market);
            Optional<Position> positionAtCycle = portfolioRepository.findByMarket(config.marketSymbol());

            SignalDecision signal = determineSignal(config, market, now);
            boolean riskAllowed = true;
            String riskReason = "RISK_SKIPPED";
            BigDecimal approvedOrderKrw = config.maxOrderKrw();

            Optional<OrderDecision> riskCandidate = orderDecisionService.decide(
                    signal,
                    market,
                    lastPrice,
                    config.maxOrderKrw()
            );
            if (riskCandidate.isPresent()) {
                RiskContext riskContext = buildRiskContext(config, riskCandidate.get(), lastPrice, now);
                RiskDecision riskDecision = riskEvaluationService.approveAndReserve(riskContext);
                riskAllowed = riskDecision.isAllowed();
                riskReason = riskDecision.reasonCode();
                if (riskAllowed) {
                    reservationId = riskDecision.reservationId();
                    approvedOrderKrw = riskDecision.approvedOrderKrw();
                }
            }

            OrderActionDecision actionDecision = orderDecisionService.decide(
                    new OrderDecisionContext(
                            signal,
                            market,
                            lastPrice,
                            lastPrice,
                            lastPrice,
                            now,
                            now,
                            approvedOrderKrw,
                            resolveMaxPositionQty(config, lastPrice),
                            resolveAvailableQuoteKrw(config, lastPrice, positionAtCycle),
                            positionAtCycle.map(Position::quantity).orElse(BigDecimal.ZERO),
                            resolveReservedQuoteKrw(openOrder),
                            resolveReservedBaseQty(openOrder),
                            positionAtCycle.map(Position::quantity).orElse(BigDecimal.ZERO),
                            new BigDecimal("0.0005"),
                            new BigDecimal("0.0020"),
                            openOrder.map(OpenOrderSnapshot::quantity).orElse(BigDecimal.ZERO),
                            riskAllowed,
                            riskReason,
                            openOrder,
                            market.value() + ":" + signal.signalAt(),
                            buildOrderPolicy(config),
                            lastOrderAt.get()
                    )
            );

            if (actionDecision.type() == OrderDecisionType.HOLD) {
                if (reservationId != null) {
                    riskEvaluationService.releaseReservation(DEFAULT_ACCOUNT_ID, reservationId);
                    reservationId = null;
                }
                successfulCycles.incrementAndGet();
                consecutiveFailures.set(0);
                return new CycleResult(true, false, actionDecision.reason());
            }

            OrderCommand command = actionDecision.command().orElseThrow();
            if (actionDecision.type() == OrderDecisionType.CANCEL) {
                cancelOrderById(command.targetOrderId());
                if (reservationId != null) {
                    riskEvaluationService.releaseReservation(DEFAULT_ACCOUNT_ID, reservationId);
                    reservationId = null;
                }
                successfulCycles.incrementAndGet();
                consecutiveFailures.set(0);
                return new CycleResult(true, false, "order canceled: " + command.reason());
            }

            String key = command.clientOrderId();
            if (orderRepository.existsByIdempotencyKey(key)) {
                if (reservationId != null) {
                    riskEvaluationService.releaseReservation(DEFAULT_ACCOUNT_ID, reservationId);
                    reservationId = null;
                }
                successfulCycles.incrementAndGet();
                return new CycleResult(true, false, "duplicate prevented by idempotency key");
            }

            if (actionDecision.type() == OrderDecisionType.MODIFY) {
                cancelOrderById(command.targetOrderId());
            }

            Order order = Order.create(
                    command.market(),
                    command.side(),
                    command.quantity(),
                    command.price(),
                    now
            );
            Order placed = exchangeTradingPort.placeOrder(order);
            orderRepository.rememberIdempotencyKey(key);
            orderRepository.save(placed);
            if (reservationId != null) {
                riskEvaluationService.releaseReservation(DEFAULT_ACCOUNT_ID, reservationId);
                reservationId = null;
            }
            lastOrderAt.set(now);

            consecutiveFailures.set(0);
            successfulCycles.incrementAndGet();
            return new CycleResult(true, true, "order placed: " + actionDecision.reason());
        } catch (Exception e) {
            if (reservationId != null) {
                riskEvaluationService.releaseReservation(DEFAULT_ACCOUNT_ID, reservationId);
            }
            int failures = consecutiveFailures.incrementAndGet();
            failedCycles.incrementAndGet();
            lastError.set(e.getMessage());
            if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
                state.set(BotRunState.CIRCUIT_OPEN);
                notificationPort.notify("Circuit breaker opened: " + e.getMessage());
            }
            return new CycleResult(true, false, "cycle failed: " + e.getMessage());
        }
    }

    @Override
    public List<Order> listOrders() {
        return orderRepository.findAll();
    }

    @Override
    public PortfolioSnapshot getPortfolioSnapshot() {
        BotConfig config = botSettingsRepository.load();
        Market market = toMarket(config.marketSymbol());
        Money lastPrice = marketDataPort.getLastPrice(market);
        Optional<Position> positionOpt = portfolioRepository.findByMarket(config.marketSymbol());

        if (positionOpt.isEmpty()) {
            return new PortfolioSnapshot(
                    config.marketSymbol(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
        }

        Position position = positionOpt.get();
        BigDecimal quantity = position.quantity();
        BigDecimal avg = position.avgPrice().value();
        BigDecimal invested = position.totalInvestment();
        BigDecimal marketValue = position.marketValue(lastPrice);
        BigDecimal realized = position.realizedPnL();
        BigDecimal unrealized = position.unrealizedPnL(lastPrice);
        BigDecimal totalPnl = realized.add(unrealized);

        return new PortfolioSnapshot(
                config.marketSymbol(),
                quantity,
                avg,
                invested,
                marketValue,
                realized,
                unrealized,
                totalPnl
        );
    }

    @Override
    public MetricsSnapshot getMetrics() {
        long success = successfulCycles.get();
        long fail = failedCycles.get();
        long total = success + fail;
        double rate = total == 0 ? 0.0 : (double) fail / total;
        RiskMetricsSnapshot risk = riskEvaluationService.snapshotMetrics();
        return new MetricsSnapshot(
                success,
                fail,
                rate,
                risk.totalDecisions(),
                risk.allowCount(),
                risk.rejectCount(),
                risk.allowWithLimitCount(),
                risk.reasonCodeCounts(),
                risk.decisionTypeCounts()
        );
    }

    private RiskContext buildRiskContext(BotConfig config, OrderDecision decision, Money lastPrice, Instant now) {
        Optional<Position> positionOpt = portfolioRepository.findByMarket(config.marketSymbol());
        BigDecimal currentExposure = positionOpt
                .map(position -> position.quantity().multiply(lastPrice.amount()))
                .orElse(BigDecimal.ZERO);
        BigDecimal realizedPnl = positionOpt.map(Position::realizedPnL).orElse(BigDecimal.ZERO);
        BigDecimal unrealizedPnl = positionOpt.map(position -> position.unrealizedPnL(lastPrice)).orElse(BigDecimal.ZERO);
        BigDecimal availableCash = config.initialCashKrw().subtract(currentExposure).max(BigDecimal.ZERO);

        RiskOrderRequest orderRequest = new RiskOrderRequest(
                DEFAULT_ACCOUNT_ID,
                decision.market(),
                decision.side(),
                decision.price(),
                decision.quantity(),
                now
        );

        RiskAccountSnapshot accountSnapshot = new RiskAccountSnapshot(
                DEFAULT_ACCOUNT_ID,
                config.initialCashKrw(),
                availableCash,
                BigDecimal.ZERO,
                currentExposure,
                realizedPnl,
                unrealizedPnl,
                BigDecimal.ZERO,
                lastOrderAt.get()
        );

        RiskMarketSnapshot marketSnapshot = new RiskMarketSnapshot(
                config.marketSymbol(),
                lastPrice.amount(),
                lastPrice.amount(),
                lastPrice.amount(),
                BigDecimal.ZERO,
                now,
                Duration.ofSeconds(5)
        );

        RiskPolicy policy = new RiskPolicy(
                new BigDecimal("5000"),
                config.maxOrderKrw(),
                config.maxExposureRatio(),
                config.maxDailyLossRatio(),
                Duration.ofSeconds(config.cooldownSeconds()),
                Duration.ofSeconds(5),
                ZoneId.of("Asia/Seoul"),
                new BigDecimal("0.0005"),
                new BigDecimal("0.0020")
        );

        return new RiskContext(
                orderRequest,
                accountSnapshot,
                marketSnapshot,
                policy,
                Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private SignalDecision determineSignal(BotConfig config, Market market, Instant now) {
        Timeframe timeframe = Timeframe.M1;
        Optional<StrategyPositionSnapshot> positionSnapshot = portfolioRepository.findByMarket(config.marketSymbol())
                .map(position -> new StrategyPositionSnapshot(
                        position.quantity().signum() >= 0 ? Side.BUY : Side.SELL,
                        position.quantity()
                ));
        // Strategy is pure: all required inputs are assembled here and injected once.
        StrategyContext context = new StrategyContext(
                config.marketSymbol(),
                marketDataPort.getRecentCandles(market, timeframe, 150, now),
                timeframe,
                now,
                positionSnapshot
        );
        return strategy.evaluate(context);
    }

    private Market toMarket(String symbol) {
        return Market.of(symbol);
    }

    private OrderMarketPolicy buildOrderPolicy(BotConfig config) {
        // Keep policy explicit so decision behavior is deterministic.
        return new OrderMarketPolicy(
                BigDecimal.ONE,
                new BigDecimal("0.00000001"),
                new BigDecimal("5000"),
                new BigDecimal("0.00000001"),
                new BigDecimal("1000"),
                new BigDecimal("0.3000"),
                BigDecimal.ONE,
                true,
                new BigDecimal("0.0200"),
                BigDecimal.ONE,
                BigDecimal.ONE,
                Duration.ofSeconds(5),
                Duration.ofSeconds(config.cooldownSeconds())
        );
    }

    private BigDecimal resolveMaxPositionQty(BotConfig config, Money lastPrice) {
        BigDecimal maxExposureKrw = config.initialCashKrw().multiply(config.maxExposureRatio());
        if (lastPrice.amount().signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return maxExposureKrw.divide(lastPrice.amount(), 8, RoundingMode.DOWN);
    }

    private BigDecimal resolveAvailableQuoteKrw(
            BotConfig config,
            Money lastPrice,
            Optional<Position> position
    ) {
        BigDecimal currentExposure = position
                .map(pos -> pos.quantity().multiply(lastPrice.amount()))
                .orElse(BigDecimal.ZERO);
        return config.initialCashKrw().subtract(currentExposure).max(BigDecimal.ZERO);
    }

    private BigDecimal resolveReservedQuoteKrw(Optional<OpenOrderSnapshot> openOrder) {
        return openOrder
                .filter(order -> order.side() == Side.BUY)
                .map(order -> order.price().amount().multiply(order.quantity()))
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal resolveReservedBaseQty(Optional<OpenOrderSnapshot> openOrder) {
        return openOrder
                .filter(order -> order.side() == Side.SELL)
                .map(OpenOrderSnapshot::quantity)
                .orElse(BigDecimal.ZERO);
    }

    private Optional<OpenOrderSnapshot> findLatestOpenOrder(Market market) {
        return orderRepository.findAll().stream()
                .filter(order -> order.market().equals(market))
                .filter(order -> ACTIVE_ORDER_STATUSES.contains(order.status()))
                .reduce((first, second) -> second)
                .map(order -> new OpenOrderSnapshot(
                        order.id(),
                        order.market(),
                        order.side(),
                        order.price(),
                        order.quantity(),
                        null,
                        order.createdAt()
                ));
    }

    private void cancelOrderById(String orderId) {
        exchangeTradingPort.cancelOrder(orderId);
        orderRepository.findAll().stream()
                .filter(order -> order.id().equals(orderId))
                .findFirst()
                .ifPresent(order -> {
                    if (order.canCancel()) {
                        order.cancel();
                        orderRepository.save(order);
                    }
                });
    }

    private BotStatusSnapshot snapshot() {
        return new BotStatusSnapshot(
                state.get(),
                lastCycleAt.get(),
                lastError.get(),
                consecutiveFailures.get()
        );
    }
}
