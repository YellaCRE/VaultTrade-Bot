package com.vaulttradebot.application.service;

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
import com.vaulttradebot.domain.shared.Market;
import com.vaulttradebot.domain.shared.Money;
import com.vaulttradebot.domain.shared.Order;
import com.vaulttradebot.domain.shared.Position;
import com.vaulttradebot.domain.execution.IdempotencyService;
import com.vaulttradebot.domain.ops.BotConfig;
import com.vaulttradebot.domain.ops.BotRunState;
import com.vaulttradebot.domain.ops.BotStatusSnapshot;
import com.vaulttradebot.domain.ops.MetricsSnapshot;
import com.vaulttradebot.domain.portfolio.PortfolioSnapshot;
import com.vaulttradebot.domain.risk.RiskContext;
import com.vaulttradebot.domain.risk.RiskEvaluation;
import com.vaulttradebot.domain.risk.RiskEvaluationService;
import com.vaulttradebot.domain.risk.RiskPolicy;
import com.vaulttradebot.domain.trading.OrderDecision;
import com.vaulttradebot.domain.trading.OrderDecisionService;
import com.vaulttradebot.domain.trading.SignalAction;
import com.vaulttradebot.domain.trading.TradingSignal;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class BotFacadeService implements BotControlUseCase, BotConfigUseCase, RunTradingCycleUseCase, BotQueryUseCase {
    private static final Currency KRW = Currency.getInstance("KRW");
    private static final int CIRCUIT_BREAKER_THRESHOLD = 3;

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
            IdempotencyService idempotencyService
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

        try {
            BotConfig config = botSettingsRepository.load();
            Market market = toMarket(config.marketSymbol());
            Money lastPrice = marketDataPort.getLastPrice(market);

            TradingSignal signal = determineSignal(config, lastPrice);
            Optional<OrderDecision> decisionOpt = orderDecisionService.decide(
                    signal, market, lastPrice, config.maxOrderKrw()
            );
            if (decisionOpt.isEmpty()) {
                consecutiveFailures.set(0);
                successfulCycles.incrementAndGet();
                return new CycleResult(true, false, "no trade signal");
            }

            OrderDecision decision = decisionOpt.get();
            BigDecimal exposureRatio = calculateExposureRatio(config, decision);
            RiskPolicy policy = new RiskPolicy(
                    config.maxOrderKrw(),
                    config.maxExposureRatio(),
                    config.maxDailyLossRatio(),
                    Duration.ofSeconds(config.cooldownSeconds())
            );
            RiskContext context = new RiskContext(
                    decision.price().amount().multiply(decision.quantity()),
                    exposureRatio,
                    BigDecimal.ZERO,
                    now,
                    lastOrderAt.get()
            );

            RiskEvaluation riskEvaluation = riskEvaluationService.evaluate(context, policy);
            if (!riskEvaluation.allowed()) {
                successfulCycles.incrementAndGet();
                return new CycleResult(true, false, riskEvaluation.reason());
            }

            String key = idempotencyService.generateKey(
                    decision.market().symbol(),
                    decision.side().name(),
                    decision.quantity().toPlainString(),
                    now,
                    decision.reason()
            );
            if (orderRepository.existsByIdempotencyKey(key)) {
                successfulCycles.incrementAndGet();
                return new CycleResult(true, false, "duplicate prevented by idempotency key");
            }

            Order order = Order.create(
                    decision.market(),
                    decision.side(),
                    decision.quantity(),
                    decision.price(),
                    now
            );
            order.submit();
            Order placed = exchangeTradingPort.placeOrder(order);
            orderRepository.rememberIdempotencyKey(key);
            orderRepository.save(placed);
            lastOrderAt.set(now);

            consecutiveFailures.set(0);
            successfulCycles.incrementAndGet();
            return new CycleResult(true, true, "order placed");
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
                    config.initialCashKrw(),
                    BigDecimal.ZERO
            );
        }

        Position position = positionOpt.get();
        BigDecimal quantity = position.quantity();
        BigDecimal avg = position.averageEntryPrice().amount();
        BigDecimal invested = avg.multiply(quantity);
        BigDecimal currentValue = lastPrice.amount().multiply(quantity);
        BigDecimal unrealized = currentValue.subtract(invested);
        BigDecimal cash = config.initialCashKrw().subtract(invested).max(BigDecimal.ZERO);

        return new PortfolioSnapshot(config.marketSymbol(), quantity, avg, cash, unrealized);
    }

    @Override
    public MetricsSnapshot getMetrics() {
        long success = successfulCycles.get();
        long fail = failedCycles.get();
        long total = success + fail;
        double rate = total == 0 ? 0.0 : (double) fail / total;
        return new MetricsSnapshot(success, fail, rate);
    }

    private TradingSignal determineSignal(BotConfig config, Money lastPrice) {
        if (lastPrice.amount().compareTo(config.buyThresholdPrice()) <= 0) {
            return new TradingSignal(SignalAction.BUY, "price under threshold");
        }
        return new TradingSignal(SignalAction.HOLD, "no signal");
    }

    private BigDecimal calculateExposureRatio(BotConfig config, OrderDecision decision) {
        Optional<Position> position = portfolioRepository.findByMarket(config.marketSymbol());
        BigDecimal currentExposure = position
                .map(value -> value.quantity().multiply(decision.price().amount()))
                .orElse(BigDecimal.ZERO);
        BigDecimal requested = decision.price().amount().multiply(decision.quantity());
        BigDecimal totalExposure = currentExposure.add(requested);
        return totalExposure.divide(config.initialCashKrw(), 8, java.math.RoundingMode.HALF_UP);
    }

    private Market toMarket(String symbol) {
        String[] parts = symbol.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("market symbol must be QUOTE-BASE format, e.g. KRW-BTC");
        }
        return new Market(symbol, parts[1], parts[0]);
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
