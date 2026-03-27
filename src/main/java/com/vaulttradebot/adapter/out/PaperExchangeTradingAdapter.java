package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.port.out.ExchangeTradingPort;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.config.VaultTradingProperties;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Quantity;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.vo.ExecutionTrade;
import com.vaulttradebot.domain.execution.Order;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "vault.trading.provider", havingValue = "paper", matchIfMissing = true)
public class PaperExchangeTradingAdapter implements ExchangeTradingPort {
    private static final BigDecimal BPS_DIVISOR = new BigDecimal("10000");

    private final ClockPort clockPort;
    private final VaultTradingProperties tradingProperties;

    public PaperExchangeTradingAdapter(ClockPort clockPort, VaultTradingProperties tradingProperties) {
        this.clockPort = clockPort;
        this.tradingProperties = tradingProperties;
    }

    @Override
    public Order placeOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }

        // Paper orders still receive an exchange-like id so cancel/sync flows behave the same as live trading.
        bindPaperExchangeId(order);
        order.acceptByExchange();
        if (tradingProperties.getPaper().isFillOnPlace()) {
            fillRemaining(order);
        }
        return order;
    }

    @Override
    public Order refreshOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (order.exchangeOrderId() == null || order.exchangeOrderId().isBlank()) {
            throw new IllegalArgumentException("exchange order id must not be blank");
        }
        // When fill-on-place is disabled, active orders complete during periodic sync instead.
        if (!tradingProperties.getPaper().isFillOnPlace()) {
            fillRemaining(order);
        }
        return order;
    }

    @Override
    public void cancelOrder(String orderId) {
        // No-op in local paper adapter.
    }

    private void bindPaperExchangeId(Order order) {
        if (order.exchangeOrderId() == null || order.exchangeOrderId().isBlank()) {
            order.bindExchangeOrderId("paper-" + UUID.randomUUID());
        }
    }

    private void fillRemaining(Order order) {
        BigDecimal remainingQuantity = order.originalQuantity()
                .subtract(order.executedQuantity())
                .value();
        if (remainingQuantity.signum() == 0) {
            return;
        }

        // Paper fills simulate a single remaining execution using configurable slippage and fee settings.
        Money executionPrice = applySlippage(order.price(), order.side());
        order.execute(new ExecutionTrade(
                "paper-trade-" + UUID.randomUUID(),
                executionPrice,
                Quantity.of(remainingQuantity),
                calculateFee(executionPrice, remainingQuantity),
                clockPort.now()
        ));
    }

    private Money applySlippage(Money price, Side side) {
        BigDecimal slippageBps = sanitize(tradingProperties.getPaper().getSlippageBps());
        if (slippageBps.signum() == 0) {
            return price;
        }

        BigDecimal multiplier = switch (side) {
            case BUY -> BigDecimal.ONE.add(slippageBps.divide(BPS_DIVISOR, 8, RoundingMode.HALF_UP));
            case SELL -> BigDecimal.ONE.subtract(slippageBps.divide(BPS_DIVISOR, 8, RoundingMode.HALF_UP));
        };
        if (multiplier.signum() <= 0) {
            throw new IllegalStateException("paper slippage produced non-positive execution price");
        }
        return Money.of(price.amount().multiply(multiplier), price.currency());
    }

    private Money calculateFee(Money executionPrice, BigDecimal quantity) {
        BigDecimal feeRate = sanitize(tradingProperties.getPaper().getFeeRate());
        if (feeRate.signum() == 0) {
            return Money.of(BigDecimal.ZERO, executionPrice.currency());
        }

        BigDecimal executedAmount = executionPrice.amount().multiply(quantity);
        return Money.of(executedAmount.multiply(feeRate).setScale(0, RoundingMode.HALF_UP), executionPrice.currency());
    }

    private BigDecimal sanitize(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }
}
