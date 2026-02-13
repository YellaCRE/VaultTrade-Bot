package com.vaulttradebot.domain.shared;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Order {
    private final String id;
    private final Market market;
    private final Side side;
    private final BigDecimal quantity;
    private final Money price;
    private final Instant createdAt;
    private OrderState state;

    public Order(
            String id,
            Market market,
            Side side,
            BigDecimal quantity,
            Money price,
            Instant createdAt,
            OrderState state
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (market == null || side == null || quantity == null || price == null || createdAt == null || state == null) {
            throw new IllegalArgumentException("order fields must not be null");
        }
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.id = id;
        this.market = market;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.createdAt = createdAt;
        this.state = state;
    }

    public static Order create(Market market, Side side, BigDecimal quantity, Money price, Instant createdAt) {
        return new Order(UUID.randomUUID().toString(), market, side, quantity, price, createdAt, OrderState.CREATED);
    }

    public void submit() {
        this.state = OrderState.SUBMITTED;
    }

    public void markFilled() {
        this.state = OrderState.FILLED;
    }

    public void cancel() {
        this.state = OrderState.CANCELED;
    }

    public String id() {
        return id;
    }

    public Market market() {
        return market;
    }

    public Side side() {
        return side;
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public Money price() {
        return price;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public OrderState state() {
        return state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Order order)) {
            return false;
        }
        return Objects.equals(id, order.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
