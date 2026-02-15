package com.vaulttradebot.domain.trading.model.sizing;

import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.vo.OrderType;
import java.math.BigDecimal;
import java.math.RoundingMode;

import com.vaulttradebot.domain.trading.model.sizing.vo.QuantityCalculationResult;
import com.vaulttradebot.domain.trading.model.sizing.snapshot.AccountSnapshot;
import com.vaulttradebot.domain.trading.model.sizing.snapshot.ExecutionSnapshot;
import com.vaulttradebot.domain.trading.model.sizing.vo.ExchangeConstraints;
import com.vaulttradebot.domain.trading.model.sizing.vo.QuantityCalculationRequest;
import org.springframework.stereotype.Component;

/** Deterministic quantity calculator with exchange, risk, and execution checks. */
@Component
public class QuantityCalculator {
    private static final int QTY_SCALE = 8;
    private static final int DIV_SCALE = 16;

    /** Runs sizing pipeline and returns tradable quantity or hold reason. */
    public QuantityCalculationResult calculate(QuantityCalculationRequest request) {
        BigDecimal referencePrice = resolveReferencePrice(request);
        if (referencePrice.signum() <= 0) {
            return QuantityCalculationResult.hold("invalid pricing reference");
        }

        BigDecimal score = BigDecimal.valueOf(request.signalStrength());
        BigDecimal rawOrderKrw = request.targetOrderKrw()
                .min(request.riskCaps().maxOrderKrw())
                .multiply(score)
                .setScale(0, RoundingMode.DOWN);
        if (rawOrderKrw.signum() <= 0) {
            return QuantityCalculationResult.hold("raw quantity is zero");
        }

        BigDecimal rawQty = rawOrderKrw.divide(referencePrice, DIV_SCALE, RoundingMode.DOWN);
        BigDecimal cappedByAccountAndRisk = applyAccountAndRiskCaps(rawQty, referencePrice, request);
        if (cappedByAccountAndRisk.signum() <= 0) {
            return QuantityCalculationResult.hold("account or risk cap reached");
        }

        BigDecimal constrainedQty = cappedByAccountAndRisk.min(request.exchangeConstraints().maxQuantity());
        BigDecimal quantized = quantizeDown(constrainedQty, request.exchangeConstraints().quantityStep());
        if (quantized.signum() <= 0 || quantized.compareTo(request.exchangeConstraints().minQuantity()) < 0) {
            return QuantityCalculationResult.hold("below minimum quantity");
        }

        BigDecimal notional = referencePrice.multiply(quantized);
        if (notional.compareTo(request.exchangeConstraints().minNotionalKrw()) < 0) {
            if (!request.riskCaps().allowStepUpForMinNotional()) {
                return QuantityCalculationResult.hold("below minimum notional");
            }
            BigDecimal steppedUp = stepUpToMinNotional(referencePrice, quantized, request);
            if (steppedUp.signum() <= 0) {
                return QuantityCalculationResult.hold("cannot step up to minimum notional");
            }
            quantized = steppedUp;
        }

        BigDecimal qualityCapped = applyExecutionQualityCap(quantized, request);
        if (qualityCapped.signum() <= 0 || qualityCapped.compareTo(request.exchangeConstraints().minQuantity()) < 0) {
            return QuantityCalculationResult.hold("execution quality cap rejected order");
        }

        return QuantityCalculationResult.tradable(qualityCapped.setScale(QTY_SCALE, RoundingMode.DOWN));
    }

    private BigDecimal resolveReferencePrice(QuantityCalculationRequest request) {
        OrderType orderType = request.orderType();
        ExecutionSnapshot execution = request.executionSnapshot();
        Money last = execution.lastPrice();
        if (last.amount().signum() <= 0) {
            return BigDecimal.ZERO;
        }

        if (orderType == OrderType.MARKET) {
            BigDecimal buffer = BigDecimal.ONE.add(execution.slippageBufferRatio());
            if (request.side() == Side.BUY) {
                BigDecimal ask = priceOrFallback(execution.bestAskPrice(), last).amount();
                return ask.multiply(buffer);
            }
            BigDecimal bid = priceOrFallback(execution.bestBidPrice(), last).amount();
            BigDecimal downBuffer = BigDecimal.ONE.subtract(execution.slippageBufferRatio());
            return bid.multiply(downBuffer.max(BigDecimal.ZERO));
        }
        return request.limitPrice().amount();
    }

    private Money priceOrFallback(Money preferred, Money fallback) {
        if (preferred != null && preferred.amount().signum() > 0) {
            return preferred;
        }
        return fallback;
    }

    private BigDecimal applyAccountAndRiskCaps(
            BigDecimal qty,
            BigDecimal referencePrice,
            QuantityCalculationRequest request
    ) {
        AccountSnapshot account = request.accountSnapshot();
        BigDecimal feeRatio = request.executionSnapshot().feeRatio();
        BigDecimal result = qty;

        if (request.side() == Side.BUY) {
            BigDecimal cash = account.availableQuoteKrw().subtract(account.reservedQuoteKrw()).max(BigDecimal.ZERO);
            BigDecimal denominator = referencePrice.multiply(BigDecimal.ONE.add(feeRatio));
            if (denominator.signum() <= 0) {
                return BigDecimal.ZERO;
            }
            BigDecimal maxByCash = cash.divide(denominator, DIV_SCALE, RoundingMode.DOWN);
            BigDecimal effectiveBase = account.currentBaseQuantity().add(account.sameSideOpenOrderQuantity());
            BigDecimal remainingPosition = request.riskCaps().maxPositionQuantity().subtract(effectiveBase).max(BigDecimal.ZERO);
            result = result.min(maxByCash).min(remainingPosition);
        } else {
            BigDecimal sellable = account.availableBaseQuantity().subtract(account.reservedBaseQuantity()).max(BigDecimal.ZERO);
            result = result.min(sellable);
        }

        return result.max(BigDecimal.ZERO);
    }

    private BigDecimal stepUpToMinNotional(
            BigDecimal referencePrice,
            BigDecimal currentQty,
            QuantityCalculationRequest request
    ) {
        ExchangeConstraints exchange = request.exchangeConstraints();
        BigDecimal needed = exchange.minNotionalKrw().divide(referencePrice, DIV_SCALE, RoundingMode.CEILING);
        BigDecimal stepped = quantizeUp(needed, exchange.quantityStep());
        if (stepped.compareTo(currentQty) <= 0) {
            return currentQty;
        }
        if (stepped.compareTo(exchange.maxQuantity()) > 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal capped = applyAccountAndRiskCaps(stepped, referencePrice, request);
        if (capped.compareTo(stepped) < 0) {
            return BigDecimal.ZERO;
        }

        return stepped;
    }

    private BigDecimal applyExecutionQualityCap(BigDecimal qty, QuantityCalculationRequest request) {
        BigDecimal topBookQty = request.executionSnapshot().topBookQuantity();
        if (topBookQty.signum() <= 0) {
            return qty;
        }

        BigDecimal depthCap = topBookQty.multiply(request.executionSnapshot().depthFactor());
        BigDecimal byDepth = qty.min(depthCap);
        if (byDepth.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal impactRatio = byDepth.divide(topBookQty, DIV_SCALE, RoundingMode.HALF_UP);
        if (impactRatio.compareTo(request.riskCaps().maxSlippageRatio()) > 0) {
            BigDecimal impactCap = topBookQty.multiply(request.riskCaps().maxSlippageRatio());
            byDepth = byDepth.min(impactCap);
        }

        return quantizeDown(byDepth, request.exchangeConstraints().quantityStep());
    }

    private BigDecimal quantizeDown(BigDecimal value, BigDecimal step) {
        BigDecimal steps = value.divide(step, 0, RoundingMode.DOWN);
        return steps.multiply(step).setScale(QTY_SCALE, RoundingMode.DOWN);
    }

    private BigDecimal quantizeUp(BigDecimal value, BigDecimal step) {
        BigDecimal steps = value.divide(step, 0, RoundingMode.CEILING);
        return steps.multiply(step).setScale(QTY_SCALE, RoundingMode.DOWN);
    }
}
