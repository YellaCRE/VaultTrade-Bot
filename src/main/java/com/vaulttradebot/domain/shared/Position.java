package com.vaulttradebot.domain.shared;

import java.math.BigDecimal;

public class Position {
    private final Market market;
    private BigDecimal quantity;
    private Money averageEntryPrice;

    public Position(Market market, BigDecimal quantity, Money averageEntryPrice) {
        if (market == null || quantity == null || averageEntryPrice == null) {
            throw new IllegalArgumentException("position fields must not be null");
        }
        if (quantity.signum() < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
        this.market = market;
        this.quantity = quantity;
        this.averageEntryPrice = averageEntryPrice;
    }

    public void increase(BigDecimal addQuantity, Money executionPrice) {
        if (addQuantity == null || executionPrice == null) {
            throw new IllegalArgumentException("execution values must not be null");
        }
        if (addQuantity.signum() <= 0) {
            throw new IllegalArgumentException("addQuantity must be positive");
        }

        BigDecimal totalCost = averageEntryPrice.amount().multiply(quantity)
                .add(executionPrice.amount().multiply(addQuantity));
        BigDecimal newQuantity = quantity.add(addQuantity);

        this.quantity = newQuantity;
        this.averageEntryPrice = Money.of(
                totalCost.divide(newQuantity, 8, java.math.RoundingMode.HALF_UP),
                executionPrice.currency()
        );
    }

    public void decrease(BigDecimal reduceQuantity) {
        if (reduceQuantity == null || reduceQuantity.signum() <= 0) {
            throw new IllegalArgumentException("reduceQuantity must be positive");
        }
        if (reduceQuantity.compareTo(quantity) > 0) {
            throw new IllegalArgumentException("cannot reduce more than current quantity");
        }
        this.quantity = quantity.subtract(reduceQuantity);
    }

    public Market market() {
        return market;
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public Money averageEntryPrice() {
        return averageEntryPrice;
    }
}
