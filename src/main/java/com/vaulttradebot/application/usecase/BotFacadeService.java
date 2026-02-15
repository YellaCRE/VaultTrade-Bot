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
import com.vaulttradebot.domain.ops.BotConfig;
import com.vaulttradebot.domain.ops.BotRunState;
import com.vaulttradebot.domain.portfolio.Position;
import com.vaulttradebot.domain.risk.snapshot.RiskAccountSnapshot;
import com.vaulttradebot.domain.risk.vo.RiskContext;
import com.vaulttradebot.domain.risk.vo.RiskDecision;
import com.vaulttradebot.domain.risk.RiskEvaluationService;
import com.vaulttradebot.domain.risk.snapshot.RiskMarketSnapshot;
import com.vaulttradebot.domain.risk.snapshot.RiskMetricsSnapshot;
import com.vaulttradebot.domain.risk.RiskOrderRequest;
import com.vaulttradebot.domain.risk.RiskPolicy;
import com.vaulttradebot.domain.trading.OrderDecision;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class BotFacadeService implements BotControlUseCase, BotConfigUseCase, RunTradingCycleUseCase, BotQueryUseCase {
    private static final int CIRCUIT_BREAKER_THRESHOLD = 3;
    private static final String DEFAULT_ACCOUNT_ID = "default-account";

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

            SignalDecision signal = determineSignal(config, market, now);
            Optional<OrderDecision> decisionOpt = orderDecisionService.decide(
                    signal, market, lastPrice, config.maxOrderKrw()
            );
            if (decisionOpt.isEmpty()) {
                consecutiveFailures.set(0);
                successfulCycles.incrementAndGet();
                return new CycleResult(true, false, "no trade signal: " + signal.reason());
            }

            OrderDecision decision = decisionOpt.get();
            RiskContext riskContext = buildRiskContext(config, decision, lastPrice, now);
            RiskDecision riskDecision = riskEvaluationService.approveAndReserve(riskContext);
            if (!riskDecision.isAllowed()) {
                successfulCycles.incrementAndGet();
                return new CycleResult(true, false, "risk rejected: " + riskDecision.reasonCode());
            }
            reservationId = riskDecision.reservationId();

            BigDecimal approvedQuantity = riskDecision.approvedOrderKrw()
                    .divide(decision.price().amount(), 8, RoundingMode.DOWN);
            if (approvedQuantity.signum() <= 0) {
                riskEvaluationService.releaseReservation(DEFAULT_ACCOUNT_ID, reservationId);
                successfulCycles.incrementAndGet();
                return new CycleResult(true, false, "risk rejected: approved quantity is zero");
            }

            String key = idempotencyService.generateKey(
                    decision.market().value(),
                    decision.side().name(),
                    approvedQuantity.toPlainString(),
                    now,
                    riskDecision.reasonCode()
            );
            if (orderRepository.existsByIdempotencyKey(key)) {
                riskEvaluationService.releaseReservation(DEFAULT_ACCOUNT_ID, reservationId);
                successfulCycles.incrementAndGet();
                return new CycleResult(true, false, "duplicate prevented by idempotency key");
            }

            Order order = Order.create(
                    decision.market(),
                    decision.side(),
                    approvedQuantity,
                    decision.price(),
                    now
            );
            Order placed = exchangeTradingPort.placeOrder(order);
            orderRepository.rememberIdempotencyKey(key);
            orderRepository.save(placed);
            riskEvaluationService.releaseReservation(DEFAULT_ACCOUNT_ID, reservationId);
            reservationId = null;
            lastOrderAt.set(now);

            consecutiveFailures.set(0);
            successfulCycles.incrementAndGet();
            return new CycleResult(true, true, "order placed: " + riskDecision.reasonCode());
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

    private BotStatusSnapshot snapshot() {
        return new BotStatusSnapshot(
                state.get(),
                lastCycleAt.get(),
                lastError.get(),
                consecutiveFailures.get()
        );
    }
}
