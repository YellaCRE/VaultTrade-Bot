package com.vaulttradebot.application.usecase;

import com.vaulttradebot.application.port.in.BotConfigUseCase;
import com.vaulttradebot.application.port.in.BotControlUseCase;
import com.vaulttradebot.application.port.in.BotQueryUseCase;
import com.vaulttradebot.application.port.in.RunTradingCycleUseCase;
import com.vaulttradebot.application.outbox.OutboxMessage;
import com.vaulttradebot.application.idempotency.IdempotencyHasher;
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
import com.vaulttradebot.application.query.BotStatusSnapshot;
import com.vaulttradebot.application.query.MetricsSnapshot;
import com.vaulttradebot.application.query.PortfolioSnapshot;
import com.vaulttradebot.domain.common.vo.Candle;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.common.vo.Timeframe;
import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.domain.execution.vo.OrderStatus;
import com.vaulttradebot.domain.ops.BotConfig;
import com.vaulttradebot.domain.ops.BotRunState;
import com.vaulttradebot.domain.portfolio.Position;
import com.vaulttradebot.domain.risk.RiskDecision;
import com.vaulttradebot.domain.risk.RiskEvaluationService;
import com.vaulttradebot.domain.risk.RiskOrderRequest;
import com.vaulttradebot.domain.risk.RiskPolicy;
import com.vaulttradebot.domain.risk.snapshot.RiskAccountSnapshot;
import com.vaulttradebot.domain.risk.snapshot.RiskMarketSnapshot;
import com.vaulttradebot.domain.risk.snapshot.RiskMetricsSnapshot;
import com.vaulttradebot.domain.risk.vo.RiskContext;
import com.vaulttradebot.domain.trading.OrderActionDecision;
import com.vaulttradebot.domain.trading.OrderCommand;
import com.vaulttradebot.domain.trading.OrderDecisionService;
import com.vaulttradebot.domain.trading.OrderMarketPolicy;
import com.vaulttradebot.domain.trading.model.strategy.Strategy;
import com.vaulttradebot.domain.trading.model.strategy.snapshot.StrategyPositionSnapshot;
import com.vaulttradebot.domain.trading.model.strategy.vo.SignalDecision;
import com.vaulttradebot.domain.trading.model.strategy.vo.StrategyContext;
import com.vaulttradebot.domain.trading.snapshot.OpenOrderSnapshot;
import com.vaulttradebot.domain.trading.vo.OrderDecision;
import com.vaulttradebot.domain.trading.vo.OrderDecisionContext;
import com.vaulttradebot.domain.trading.vo.OrderDecisionType;
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
    private final PortfolioRepository portfolioRepository;
    private final OrderRepository orderRepository;
    private final NotificationPort notificationPort;
    private final ClockPort clockPort;
    private final OrderDecisionService orderDecisionService;
    private final RiskEvaluationService riskEvaluationService;
    private final OrderOutboxTransactionPort orderOutboxTransactionPort;
    private final OutboxRepository outboxRepository;
    private final TradingCycleSnapshotRepository tradingCycleSnapshotRepository;
    private final TradingCycleLockPort tradingCycleLockPort;
    private final Strategy strategy;

    private final AtomicReference<BotRunState> state = new AtomicReference<>(BotRunState.STOPPED);
    private final AtomicReference<Instant> lastCycleAt = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final AtomicReference<Instant> lastOrderAt = new AtomicReference<>();
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong successfulCycles = new AtomicLong(0);
    private final AtomicLong failedCycles = new AtomicLong(0);
    private final AtomicLong lockSkippedCycles = new AtomicLong(0);

    public BotFacadeService(
            BotSettingsRepository botSettingsRepository,
            MarketDataPort marketDataPort,
            PortfolioRepository portfolioRepository,
            OrderRepository orderRepository,
            NotificationPort notificationPort,
            ClockPort clockPort,
            OrderDecisionService orderDecisionService,
            RiskEvaluationService riskEvaluationService,
            OrderOutboxTransactionPort orderOutboxTransactionPort,
            OutboxRepository outboxRepository,
            TradingCycleSnapshotRepository tradingCycleSnapshotRepository,
            TradingCycleLockPort tradingCycleLockPort,
            Strategy strategy
    ) {
        this.botSettingsRepository = botSettingsRepository;
        this.marketDataPort = marketDataPort;
        this.portfolioRepository = portfolioRepository;
        this.orderRepository = orderRepository;
        this.notificationPort = notificationPort;
        this.clockPort = clockPort;
        this.orderDecisionService = orderDecisionService;
        this.riskEvaluationService = riskEvaluationService;
        this.orderOutboxTransactionPort = orderOutboxTransactionPort;
        this.outboxRepository = outboxRepository;
        this.tradingCycleSnapshotRepository = tradingCycleSnapshotRepository;
        this.tradingCycleLockPort = tradingCycleLockPort;
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
    public CycleResult runCycle() {
        // Step 1) Mark cycle start time for latency and status tracking.
        Instant cycleStart = clockPort.now();
        lastCycleAt.set(cycleStart);

        // Step 2) Skip early when bot is not in RUNNING state.
        if (state.get() != BotRunState.RUNNING) {
            successfulCycles.incrementAndGet();
            return new CycleResult(false, false, "bot is not running");
        }

        BotConfig config = botSettingsRepository.load();
        Timeframe timeframe = Timeframe.M1;
        String strategyId = strategy.getClass().getSimpleName();
        String lockKey = config.marketSymbol() + "|" + strategyId;

        // Step 3) Acquire per-(pair,strategy) lock to prevent concurrent duplicate cycles.
        if (!tradingCycleLockPort.tryAcquire(lockKey)) {
            lockSkippedCycles.incrementAndGet();
            successfulCycles.incrementAndGet();
            return new CycleResult(false, false, "cycle skipped: lock not acquired");
        }

        try {
            // Step 4) Run the locked orchestration flow with fixed boundaries.
            return runLockedCycle(config, timeframe, strategyId, cycleStart);
        } finally {
            // Step 5) Always release lock even if the cycle fails.
            tradingCycleLockPort.release(lockKey);
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

    private CycleResult runLockedCycle(
            BotConfig config,
            Timeframe timeframe,
            String strategyId,
            Instant cycleStart
    ) {
        Market market = toMarket(config.marketSymbol());
        List<Candle> candles;
        try {
            // Load market data window once so all downstream decisions use the same snapshot base.
            candles = marketDataPort.getRecentCandles(market, timeframe, 150, cycleStart);
        } catch (Exception marketError) {
            successfulCycles.incrementAndGet();
            consecutiveFailures.set(0);
            return new CycleResult(false, false, "cycle skipped: market data unavailable");
        }

        // Pin cycle to the latest closed candle timestamp for deterministic replay.
        Instant dataTimestamp = resolveDataTimestamp(candles, timeframe, cycleStart);
        if (dataTimestamp == null) {
            successfulCycles.incrementAndGet();
            consecutiveFailures.set(0);
            return new CycleResult(false, false, "cycle skipped: no closed candle");
        }

        // Use deterministic cycle id to guarantee idempotent re-entry behavior.
        String cycleId = buildCycleId(strategyId, market, timeframe, dataTimestamp);
        Optional<TradingCycleSnapshot> existingCycle = tradingCycleSnapshotRepository.findByCycleId(cycleId);
        if (existingCycle.isPresent()) {
            successfulCycles.incrementAndGet();
            consecutiveFailures.set(0);
            return existingCycle.get().toCycleResult();
        }

        try {
            return executeCycleDecision(config, market, timeframe, candles, dataTimestamp, strategyId, cycleId, cycleStart);
        } catch (Exception e) {
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

    private CycleResult executeCycleDecision(
            BotConfig config,
            Market market,
            Timeframe timeframe,
            List<Candle> candles,
            Instant dataTimestamp,
            String strategyId,
            String cycleId,
            Instant cycleStart
    ) {
        // Build immutable input snapshot used by strategy/risk/decision layers.
        Money lastPrice = marketDataPort.getLastPrice(market);
        Optional<OpenOrderSnapshot> openOrder = findLatestOpenOrder(market);
        Optional<Position> positionAtCycle = portfolioRepository.findByMarket(config.marketSymbol());

        SignalDecision signal;
        try {
            // Evaluate strategy with fixed evaluation timestamp to avoid repaint issues.
            signal = determineSignal(config, dataTimestamp, candles, timeframe);
        } catch (Exception strategyError) {
            return holdWithSnapshot(
                    cycleId,
                    strategyId,
                    timeframe,
                    dataTimestamp,
                    lastPrice,
                    config,
                    positionAtCycle,
                    "HOLD",
                    "strategy evaluation failed",
                    "STRATEGY_ERROR: " + safeError(strategyError),
                    cycleStart
            );
        }

        boolean riskAllowed = true;
        String riskReason = "RISK_SKIPPED";
        BigDecimal approvedOrderKrw = config.maxOrderKrw();

        try {
            // Evaluate risk before building final order action decision.
            Optional<OrderDecision> riskCandidate = orderDecisionService.decide(
                    signal,
                    market,
                    lastPrice,
                    config.maxOrderKrw()
            );
            if (riskCandidate.isPresent()) {
                RiskContext riskContext = buildRiskContext(config, riskCandidate.get(), lastPrice, dataTimestamp);
                RiskDecision riskDecision = riskEvaluationService.approveAndReserve(riskContext);
                riskAllowed = riskDecision.isAllowed();
                riskReason = riskDecision.reasonCode();
                if (riskAllowed) {
                    approvedOrderKrw = riskDecision.approvedOrderKrw();
                }
            }
        } catch (Exception riskError) {
            return holdWithSnapshot(
                    cycleId,
                    strategyId,
                    timeframe,
                    dataTimestamp,
                    lastPrice,
                    config,
                    positionAtCycle,
                    signal.action().name(),
                    signal.reason(),
                    "RISK_ERROR: " + safeError(riskError),
                    cycleStart
            );
        }

        OrderActionDecision actionDecision = orderDecisionService.decide(
                new OrderDecisionContext(
                        signal,
                        market,
                        lastPrice,
                        lastPrice,
                        lastPrice,
                        dataTimestamp,
                        cycleStart,
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
                        cycleId,
                        buildOrderPolicy(config),
                        lastOrderAt.get()
                )
        );

        String outboxEventId = null;
        OutboxMessage outboxMessage = null;
        if (actionDecision.type() != OrderDecisionType.HOLD) {
            OrderCommand command = actionDecision.command().orElseThrow();
            // Convert execution intent into durable outbox event for async adapter processing.
            outboxEventId = buildOutboxEventId(cycleId, actionDecision.type().name());
            outboxMessage = buildOrderCommandRequestedEvent(
                    outboxEventId,
                    cycleId,
                    strategyId,
                    dataTimestamp,
                    actionDecision,
                    command,
                    cycleStart
            );
            lastOrderAt.set(cycleStart);
        }

        TradingCycleSnapshot snapshot = buildSnapshot(
                cycleId,
                strategyId,
                timeframe,
                dataTimestamp,
                lastPrice,
                config,
                positionAtCycle,
                signal.action().name(),
                signal.reason(),
                riskAllowed,
                riskReason,
                actionDecision,
                outboxEventId,
                null,
                cycleStart
        );

        // Persist cycle snapshot and outbox atomically to keep decision and command request consistent.
        persistCycle(snapshot, outboxMessage);
        successfulCycles.incrementAndGet();
        consecutiveFailures.set(0);

        boolean orderRequested = actionDecision.type() != OrderDecisionType.HOLD;
        String message = orderRequested
                ? "order command requested: " + actionDecision.reason()
                : actionDecision.reason();
        return new CycleResult(true, orderRequested, message);
    }

    private CycleResult holdWithSnapshot(
            String cycleId,
            String strategyId,
            Timeframe timeframe,
            Instant dataTimestamp,
            Money lastPrice,
            BotConfig config,
            Optional<Position> positionAtCycle,
            String signalAction,
            String signalReason,
            String holdReason,
            Instant cycleStart
    ) {
        OrderActionDecision hold = OrderActionDecision.hold(holdReason);
        TradingCycleSnapshot snapshot = buildSnapshot(
                cycleId,
                strategyId,
                timeframe,
                dataTimestamp,
                lastPrice,
                config,
                positionAtCycle,
                signalAction,
                signalReason,
                false,
                "EVALUATION_ERROR",
                hold,
                null,
                holdReason,
                cycleStart
        );
        persistCycle(snapshot, null);
        successfulCycles.incrementAndGet();
        consecutiveFailures.set(0);
        return new CycleResult(true, false, holdReason);
    }

    private void persistCycle(TradingCycleSnapshot snapshot, OutboxMessage outboxMessage) {
        orderOutboxTransactionPort.execute(() -> {
            tradingCycleSnapshotRepository.save(snapshot);
            if (outboxMessage != null) {
                // Store execution request in outbox instead of calling exchange synchronously.
                outboxRepository.save(outboxMessage);
            }
        });
    }

    private TradingCycleSnapshot buildSnapshot(
            String cycleId,
            String strategyId,
            Timeframe timeframe,
            Instant dataTimestamp,
            Money lastPrice,
            BotConfig config,
            Optional<Position> positionAtCycle,
            String signalAction,
            String signalReason,
            boolean riskAllowed,
            String riskReason,
            OrderActionDecision actionDecision,
            String outboxEventId,
            String errorReason,
            Instant cycleStart
    ) {
        long latencyMs = Duration.between(cycleStart, clockPort.now()).toMillis();
        Optional<OrderCommand> command = actionDecision.command();
        return new TradingCycleSnapshot(
                cycleId,
                strategyId,
                config.marketSymbol(),
                timeframe.name(),
                dataTimestamp,
                lastPrice.amount(),
                resolveAvailableQuoteKrw(config, lastPrice, positionAtCycle),
                positionAtCycle.map(Position::quantity).orElse(BigDecimal.ZERO),
                signalAction,
                signalReason,
                riskAllowed,
                riskReason,
                actionDecision.type(),
                actionDecision.reason(),
                command.map(value -> value.type().name()).orElse(null),
                command.map(value -> value.clientOrderId() == null ? value.targetOrderId() : value.clientOrderId()).orElse(null),
                outboxEventId,
                latencyMs,
                errorReason,
                clockPort.now()
        );
    }

    private OutboxMessage buildOrderCommandRequestedEvent(
            String eventId,
            String cycleId,
            String strategyId,
            Instant dataTimestamp,
            OrderActionDecision actionDecision,
            OrderCommand command,
            Instant now
    ) {
        String payload = "{"
                + "\"cycleId\":\"" + escape(cycleId) + "\","
                + "\"strategyId\":\"" + escape(strategyId) + "\","
                + "\"dataTimestamp\":\"" + dataTimestamp + "\","
                + "\"decision\":\"" + actionDecision.type().name() + "\","
                + "\"reason\":\"" + escape(actionDecision.reason()) + "\","
                + "\"commandType\":\"" + command.type().name() + "\","
                + "\"targetOrderId\":\"" + escape(valueOrEmpty(command.targetOrderId())) + "\","
                + "\"market\":\"" + escape(command.market() == null ? "" : command.market().value()) + "\","
                + "\"side\":\"" + escape(command.side() == null ? "" : command.side().name()) + "\","
                + "\"orderType\":\"" + escape(command.orderType() == null ? "" : command.orderType().name()) + "\","
                + "\"price\":\"" + escape(command.price() == null ? "" : command.price().amount().toPlainString()) + "\","
                + "\"quantity\":\"" + escape(command.quantity() == null ? "" : command.quantity().toPlainString()) + "\","
                + "\"clientOrderId\":\"" + escape(valueOrEmpty(command.clientOrderId())) + "\""
                + "}";

        return new OutboxMessage(
                eventId,
                "TradingCycle",
                cycleId,
                "OrderCommandRequested",
                payload,
                1,
                dataTimestamp,
                now,
                null,
                0,
                null,
                now,
                null
        );
    }

    private String buildCycleId(String strategyId, Market market, Timeframe timeframe, Instant dataTimestamp) {
        String raw = strategyId + "|" + market.value() + "|" + timeframe.name() + "|" + dataTimestamp;
        return IdempotencyHasher.sha256(raw);
    }

    private String buildOutboxEventId(String cycleId, String action) {
        String raw = cycleId + ":" + action;
        return IdempotencyHasher.sha256(raw);
    }

    private Instant resolveDataTimestamp(List<Candle> candles, Timeframe timeframe, Instant now) {
        return candles.stream()
                .map(candle -> candle.openTime().plus(timeframe.duration()))
                .filter(closeTime -> !closeTime.isAfter(now))
                .max(Instant::compareTo)
                .orElse(null);
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

    private SignalDecision determineSignal(
            BotConfig config,
            Instant evaluationTime,
            List<Candle> candles,
            Timeframe timeframe
    ) {
        Optional<StrategyPositionSnapshot> positionSnapshot = portfolioRepository.findByMarket(config.marketSymbol())
                .map(position -> new StrategyPositionSnapshot(
                        position.quantity().signum() >= 0 ? Side.BUY : Side.SELL,
                        position.quantity()
                ));
        // Fix strategy input time to closed-candle timestamp for deterministic replay.
        StrategyContext context = new StrategyContext(
                config.marketSymbol(),
                candles,
                timeframe,
                evaluationTime,
                positionSnapshot
        );
        return strategy.evaluate(context);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String safeError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message;
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

    private BotStatusSnapshot snapshot() {
        String error = lastError.get();
        if (lockSkippedCycles.get() > 0 && (error == null || error.isBlank())) {
            error = "lock-skipped=" + lockSkippedCycles.get();
        }
        return new BotStatusSnapshot(
                state.get(),
                lastCycleAt.get(),
                error,
                consecutiveFailures.get()
        );
    }
}
