package com.vaulttradebot.domain.trading;

import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.vo.OrderType;
import com.vaulttradebot.domain.trading.model.sizing.snapshot.AccountSnapshot;
import com.vaulttradebot.domain.trading.model.sizing.vo.ExchangeConstraints;
import com.vaulttradebot.domain.trading.model.sizing.snapshot.ExecutionSnapshot;
import com.vaulttradebot.domain.trading.model.sizing.vo.QuantityCalculationRequest;
import com.vaulttradebot.domain.trading.model.sizing.vo.QuantityCalculationResult;
import com.vaulttradebot.domain.trading.model.sizing.QuantityCalculator;
import com.vaulttradebot.domain.trading.model.sizing.vo.RiskCaps;
import com.vaulttradebot.domain.trading.snapshot.OpenOrderSnapshot;
import com.vaulttradebot.domain.trading.model.strategy.vo.SignalDecision;
import com.vaulttradebot.domain.trading.vo.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Converts validated signals into executable order intents. */
@Component
public class OrderDecisionService {
    private final QuantityCalculator quantityCalculator;

    public OrderDecisionService(QuantityCalculator quantityCalculator) {
        this.quantityCalculator = quantityCalculator;
    }

    /** Returns place/modify/cancel/hold decision from current state snapshot. */
    public OrderActionDecision decide(OrderDecisionContext context) {
        if (context.signal().action() == SignalAction.HOLD) {
            return OrderActionDecision.hold("signal is HOLD");
        }
        if (!context.riskAllowed()) {
            return context.openOrder()
                    .map(open -> OrderActionDecision.cancel(
                            OrderCommand.cancel(open.orderId(), "risk rejected: " + context.riskReason()),
                            "risk rejected with open order"
                    ))
                    .orElseGet(() -> OrderActionDecision.hold("risk rejected: " + context.riskReason()));
        }
        if (isStale(context.marketDataAsOf(), context.now(), context.marketPolicy().staleAfter())) {
            return context.openOrder()
                    .map(open -> OrderActionDecision.cancel(
                            OrderCommand.cancel(open.orderId(), "market data is stale"),
                            "market data stale with open order"
                    ))
                    .orElseGet(() -> OrderActionDecision.hold("market data is stale"));
        }
        if (isSpreadTooWide(context)) {
            return context.openOrder()
                    .map(open -> OrderActionDecision.cancel(
                            OrderCommand.cancel(open.orderId(), "spread is too wide"),
                            "spread too wide with open order"
                    ))
                    .orElseGet(() -> OrderActionDecision.hold("spread is too wide"));
        }
        if (isCooldownActive(context.lastOrderAt(), context.now(), context.marketPolicy().cooldown())) {
            return OrderActionDecision.hold("cooldown is active");
        }

        Side side = context.signal().action() == SignalAction.BUY ? Side.BUY : Side.SELL;
        Money roundedPrice = roundPrice(context.lastPrice(), context.marketPolicy().priceTick());
        BigDecimal sameSideOpenQty = context.openOrder()
                .filter(order -> order.side() == side)
                .map(OpenOrderSnapshot::quantity)
                .orElse(BigDecimal.ZERO);

        QuantityCalculationResult sizing = quantityCalculator.calculate(new QuantityCalculationRequest(
                side,
                OrderType.LIMIT,
                context.signal().confidence(),
                context.maxOrderKrw(),
                roundedPrice,
                new ExchangeConstraints(
                        context.marketPolicy().minNotionalKrw(),
                        context.marketPolicy().minQuantity(),
                        context.marketPolicy().maxQuantity(),
                        context.marketPolicy().quantityStep(),
                        context.marketPolicy().priceTick()
                ),
                new RiskCaps(
                        context.maxOrderKrw(),
                        context.maxPositionQty(),
                        context.marketPolicy().maxSlippageRatio(),
                        context.marketPolicy().allowStepUpForMinNotional()
                ),
                new ExecutionSnapshot(
                        context.lastPrice(),
                        context.bestBidPrice(),
                        context.bestAskPrice(),
                        context.slippageBufferRatio(),
                        context.feeRatio(),
                        context.topBookQty(),
                        context.marketPolicy().depthFactor()
                ),
                new AccountSnapshot(
                        context.availableQuoteKrw(),
                        context.availableBaseQty(),
                        context.reservedQuoteKrw(),
                        context.reservedBaseQty(),
                        context.currentBaseQty(),
                        sameSideOpenQty
                )
        ));
        if (!sizing.isTradable()) {
            return OrderActionDecision.hold(sizing.holdReason());
        }
        BigDecimal roundedQty = sizing.quantity();

        String clientOrderId = generateClientOrderId(
                context.marketEventId(),
                context.market(),
                side,
                roundedPrice,
                roundedQty,
                context.signal().signalAt()
        );

        if (context.openOrder().isEmpty()) {
            return OrderActionDecision.place(
                    new OrderCommand(
                            OrderCommandType.CREATE,
                            null,
                            context.market(),
                            side,
                            OrderType.LIMIT,
                            roundedPrice,
                            roundedQty,
                            clientOrderId,
                            "new signal"
                    ),
                    "place new order"
            );
        }

        OpenOrderSnapshot openOrder = context.openOrder().get();
        if (openOrder.clientOrderId() != null && openOrder.clientOrderId().equals(clientOrderId)) {
            return OrderActionDecision.hold("duplicate market event");
        }
        if (openOrder.side() != side) {
            return OrderActionDecision.cancel(
                    OrderCommand.cancel(openOrder.orderId(), "signal side changed"),
                    "cancel opposite-side order"
            );
        }
        if (shouldReplace(openOrder, roundedPrice, roundedQty, context.marketPolicy())) {
            return OrderActionDecision.modify(
                    OrderCommand.replace(
                            openOrder.orderId(),
                            context.market(),
                            side,
                            roundedPrice,
                            roundedQty,
                            clientOrderId,
                            "price or quantity changed"
                    ),
                    "replace open order"
            );
        }
        return OrderActionDecision.hold("open order is within replace tolerance");
    }

    /** Builds an order candidate from a legacy trading signal and order budget. */
    public Optional<OrderDecision> decide(
            TradingSignal signal,
            Market market,
            Money lastPrice,
            BigDecimal maxOrderKrw
    ) {
        if (signal == null || market == null || lastPrice == null || maxOrderKrw == null) {
            throw new IllegalArgumentException("inputs must not be null");
        }
        if (signal.action() == SignalAction.HOLD) {
            return Optional.empty();
        }
        if (lastPrice.amount().signum() <= 0 || maxOrderKrw.signum() <= 0) {
            return Optional.empty();
        }

        BigDecimal quantity = maxOrderKrw
                .divide(lastPrice.amount(), 8, RoundingMode.DOWN);
        if (quantity.signum() <= 0) {
            return Optional.empty();
        }

        Side side = signal.action() == SignalAction.BUY ? Side.BUY : Side.SELL;
        return Optional.of(new OrderDecision(market, side, quantity, lastPrice, signal.reason()));
    }

    /** Builds an order candidate from strategy output and order budget. */
    public Optional<OrderDecision> decide(
            SignalDecision signal,
            Market market,
            Money lastPrice,
            BigDecimal maxOrderKrw
    ) {
        if (signal == null) {
            throw new IllegalArgumentException("signal must not be null");
        }
        // Bridge strategy output to the legacy signal-based decision path.
        return decide(new TradingSignal(signal.action(), signal.reason()), market, lastPrice, maxOrderKrw);
    }

    private boolean isStale(Instant marketDataAsOf, Instant now, Duration staleAfter) {
        // Reject stale data before placing or replacing orders.
        return marketDataAsOf.plus(staleAfter).isBefore(now);
    }

    private boolean isCooldownActive(Instant lastOrderAt, Instant now, Duration cooldown) {
        if (lastOrderAt == null || cooldown.isZero()) {
            return false;
        }
        return Duration.between(lastOrderAt, now).compareTo(cooldown) < 0;
    }

    private boolean isSpreadTooWide(OrderDecisionContext context) {
        if (context.bestBidPrice() == null || context.bestAskPrice() == null) {
            return false;
        }
        BigDecimal bid = context.bestBidPrice().amount();
        BigDecimal ask = context.bestAskPrice().amount();
        if (bid.signum() <= 0 || ask.signum() <= 0 || ask.compareTo(bid) < 0) {
            return true;
        }
        BigDecimal spreadRatio = ask.subtract(bid).divide(ask, 8, RoundingMode.HALF_UP);
        return spreadRatio.compareTo(context.marketPolicy().maxSpreadRatio()) > 0;
    }

    private Money roundPrice(Money rawPrice, BigDecimal priceTick) {
        BigDecimal ticks = rawPrice.amount().divide(priceTick, 0, RoundingMode.DOWN);
        return Money.krw(ticks.multiply(priceTick));
    }

    private boolean shouldReplace(
            OpenOrderSnapshot openOrder,
            Money targetPrice,
            BigDecimal targetQty,
            OrderMarketPolicy policy
    ) {
        BigDecimal priceTicks = openOrder.price().amount()
                .subtract(targetPrice.amount())
                .abs()
                .divide(policy.priceTick(), 8, RoundingMode.HALF_UP);
        BigDecimal qtySteps = openOrder.quantity()
                .subtract(targetQty)
                .abs()
                .divide(policy.quantityStep(), 8, RoundingMode.HALF_UP);
        return priceTicks.compareTo(policy.replacePriceThresholdTicks()) > 0
                || qtySteps.compareTo(policy.replaceQuantityThresholdSteps()) > 0;
    }

    private String generateClientOrderId(
            String marketEventId,
            Market market,
            Side side,
            Money price,
            BigDecimal quantity,
            Instant signalAt
    ) {
        String seed = (marketEventId == null ? "-" : marketEventId)
                + "|" + market.value()
                + "|" + side.name()
                + "|" + price.amount().toPlainString()
                + "|" + quantity.toPlainString()
                + "|" + signalAt;
        return "vtb-" + sha256(seed).substring(0, 24);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
