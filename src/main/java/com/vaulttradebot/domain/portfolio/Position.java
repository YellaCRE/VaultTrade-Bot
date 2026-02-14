package com.vaulttradebot.domain.portfolio;

import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Price;
import com.vaulttradebot.domain.common.vo.Quantity;

import java.math.BigDecimal;

public class Position {
    private final Market market;
    private Quantity qty;
    private Price avgPrice;
    private Money realizedPnL;

    public Position(Market market, BigDecimal quantity, Money averageEntryPrice) {
        if (market == null || quantity == null || averageEntryPrice == null) {
            throw new IllegalArgumentException("position fields must not be null");
        }
        this.market = market;
        this.qty = Quantity.of(quantity);
        this.avgPrice = Price.of(averageEntryPrice.amount(), averageEntryPrice.currency());
        this.realizedPnL = Money.krw(BigDecimal.ZERO);
    }

    public void increase(BigDecimal addQuantity, Money executionPrice) {
        if (addQuantity == null || executionPrice == null) {
            throw new IllegalArgumentException("execution values must not be null");
        }
        if (addQuantity.signum() <= 0) {
            throw new IllegalArgumentException("addQuantity must be positive");
        }

        BigDecimal totalCost = avgPrice.value().multiply(qty.value())
                .add(executionPrice.amount().multiply(addQuantity));
        BigDecimal newQuantity = qty.value().add(addQuantity);

        this.qty = Quantity.of(newQuantity);
        this.avgPrice = Price.of(
                totalCost.divide(newQuantity, 8, java.math.RoundingMode.HALF_UP),
                executionPrice.currency()
        );
    }

    public void decrease(BigDecimal reduceQuantity) {
        if (reduceQuantity == null || reduceQuantity.signum() <= 0) {
            throw new IllegalArgumentException("reduceQuantity must be positive");
        }
        if (reduceQuantity.compareTo(qty.value()) > 0) {
            throw new IllegalArgumentException("cannot reduce more than current quantity");
        }
        BigDecimal newQuantity = qty.value().subtract(reduceQuantity);
        this.qty = Quantity.of(newQuantity);
    }

    public void recordSellFill(BigDecimal soldQuantity, Money executionPrice) {
        if (soldQuantity == null || executionPrice == null || soldQuantity.signum() <= 0) {
            throw new IllegalArgumentException("sell fill values must be valid");
        }
        BigDecimal pnl = executionPrice.amount().subtract(avgPrice.value()).multiply(soldQuantity);
        if (pnl.signum() < 0) {
            throw new IllegalArgumentException("realized pnl cannot be negative with Money invariant");
        }
        this.realizedPnL = realizedPnL.add(Money.krw(pnl));
        decrease(soldQuantity);
    }

    public Market market() {
        return market;
    }

    public BigDecimal quantity() {
        return qty.value();
    }

    public Money averageEntryPrice() {
        return Money.of(avgPrice.value(), avgPrice.unitCurrency());
    }

    public Price avgPrice() {
        return avgPrice;
    }

    public Quantity qty() {
        return qty;
    }

    public Money realizedPnL() {
        return realizedPnL;
    }
}
