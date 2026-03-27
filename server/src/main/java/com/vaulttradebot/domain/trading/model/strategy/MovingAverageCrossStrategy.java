package com.vaulttradebot.domain.trading.model.strategy;

import com.vaulttradebot.domain.common.vo.Candle;
import com.vaulttradebot.domain.trading.model.strategy.vo.SignalDecision;
import com.vaulttradebot.domain.trading.model.strategy.vo.StrategyContext;
import com.vaulttradebot.domain.trading.model.strategy.vo.StrategyKey;
import com.vaulttradebot.domain.trading.model.strategy.vo.StrategyState;
import com.vaulttradebot.domain.trading.vo.SignalAction;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.vaulttradebot.domain.common.vo.Timeframe;
import org.springframework.stereotype.Component;

/** Close-candle MA cross strategy with duplicate-signal suppression. */
@Component
public class MovingAverageCrossStrategy implements Strategy {
    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

    private final StrategyConfig config;
    private final Map<StrategyKey, StrategyState> states = new ConcurrentHashMap<>();

    /** Creates the strategy with safe default MA parameters. */
    public MovingAverageCrossStrategy() {
        this(new StrategyConfig("ma-cross-v1", 5, 20, 2, true));
    }

    /** Creates the strategy with an explicit MA config. */
    public MovingAverageCrossStrategy(StrategyConfig config) {
        this.config = config;
    }

    /** Evaluates MA cross on closed candles and applies cooldown/debounce rules. */
    @Override
    public SignalDecision evaluate(StrategyContext context) {
        // Normalize feed shape first so backtest/live behave consistently.
        List<Candle> normalized = normalizeCandles(context.marketDataWindow());
        // Repaint guard: evaluate only closed candles.
        List<Candle> closed = closedCandlesOnly(normalized, context.timeframe(), context.now());
        if (closed.size() < config.slowPeriod() + 1) {
            return SignalDecision.hold("INSUFFICIENT_DATA", context.now(), context.symbol(), context.timeframe());
        }

        int endIndex = closed.size() - 1;
        int prevIndex = endIndex - 1;

        BigDecimal fastPrev = averageClose(closed, prevIndex, config.fastPeriod());
        BigDecimal slowPrev = averageClose(closed, prevIndex, config.slowPeriod());
        BigDecimal fastNow = averageClose(closed, endIndex, config.fastPeriod());
        BigDecimal slowNow = averageClose(closed, endIndex, config.slowPeriod());

        if (!isFinitePositive(fastPrev, slowPrev, fastNow, slowNow)) {
            return SignalDecision.hold("INVALID_INDICATOR_VALUE", context.now(), context.symbol(), context.timeframe());
        }

        SignalAction action = SignalAction.HOLD;
        if (fastPrev.compareTo(slowPrev) <= 0 && fastNow.compareTo(slowNow) > 0) {
            action = SignalAction.BUY;
        } else if (fastPrev.compareTo(slowPrev) >= 0 && fastNow.compareTo(slowNow) < 0) {
            action = SignalAction.SELL;
        }

        Instant signalAt = closed.get(endIndex).openTime().plus(context.timeframe().duration());
        if (action == SignalAction.HOLD) {
            return SignalDecision.hold("NO_CROSS", signalAt, context.symbol(), context.timeframe());
        }
        StrategyKey key = new StrategyKey(context.symbol(), context.timeframe());
        StrategyState previousState = states.get(key);
        if (isBlockedByState(action, previousState, signalAt)) {
            return SignalDecision.hold("DEBOUNCED_OR_COOLDOWN", signalAt, context.symbol(), context.timeframe());
        }

        double confidence = calculateConfidence(fastNow, slowNow);
        String reason = "config=%s, fastPrev=%s, slowPrev=%s, fastNow=%s, slowNow=%s".formatted(
                config.configId(),
                fastPrev.toPlainString(),
                slowPrev.toPlainString(),
                fastNow.toPlainString(),
                slowNow.toPlainString()
        );

        SignalDecision decision = new SignalDecision(
                action,
                confidence,
                reason,
                signalAt,
                context.symbol(),
                context.timeframe()
        );
        Instant cooldownUntil = signalAt.plus(context.timeframe().duration().multipliedBy(config.cooldownBars()));
        states.put(key, new StrategyState(action, cooldownUntil));
        return decision;
    }

    /** Checks whether a new signal should be suppressed by state rules. */
    private boolean isBlockedByState(SignalAction action, StrategyState state, Instant signalAt) {
        if (state == null) {
            return false;
        }
        // Prevent repeated entries within cooldown or same-direction repeats.
        boolean cooldownActive = state.cooldownUntil() != null && signalAt.isBefore(state.cooldownUntil());
        boolean sameAsBefore = config.onlyOnChange() && action == state.lastSignal();
        return cooldownActive || sameAsBefore;
    }

    /** Sorts candles by time and removes duplicate open-time entries. */
    private List<Candle> normalizeCandles(List<Candle> input) {
        // Ensure time-ascending order and keep the latest duplicate candle.
        List<Candle> sorted = new ArrayList<>(input);
        sorted.sort(Comparator.comparing(Candle::openTime));
        Map<Instant, Candle> deduplicated = new LinkedHashMap<>();
        for (Candle candle : sorted) {
            if (candle != null) {
                deduplicated.put(candle.openTime(), candle);
            }
        }
        return new ArrayList<>(deduplicated.values());
    }

    /** Filters out in-progress candles and keeps only closed candles. */
    private List<Candle> closedCandlesOnly(List<Candle> candles, Timeframe timeframe, Instant now) {
        List<Candle> closed = new ArrayList<>();
        for (Candle candle : candles) {
            Instant closeTime = candle.openTime().plus(timeframe.duration());
            if (!closeTime.isAfter(now)) {
                closed.add(candle);
            }
        }
        return closed;
    }

    /** Computes a simple moving average of close prices for the given window. */
    private BigDecimal averageClose(List<Candle> candles, int endIndex, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            sum = sum.add(candles.get(i).close().value(), MC);
        }
        return sum.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
    }

    /** Guards against invalid indicator values before signal generation. */
    private boolean isFinitePositive(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value == null || value.signum() <= 0) {
                return false;
            }
        }
        return true;
    }

    /** Converts MA distance into normalized confidence [0, 1]. */
    private double calculateConfidence(BigDecimal fast, BigDecimal slow) {
        // Confidence scales with MA spread ratio, capped to [0,1].
        BigDecimal gap = fast.subtract(slow).abs(MC);
        BigDecimal ratio = gap.divide(slow, 8, RoundingMode.HALF_UP);
        return Math.min(1.0d, ratio.doubleValue() * 4.0d);
    }
}
