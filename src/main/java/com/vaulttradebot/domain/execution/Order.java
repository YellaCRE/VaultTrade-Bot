package com.vaulttradebot.domain.execution;

import com.vaulttradebot.domain.common.IdempotencyKey;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Price;
import com.vaulttradebot.domain.common.vo.Quantity;
import com.vaulttradebot.domain.common.vo.Side;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Order {
    private final String id;
    private final Market market;
    private final OrderType orderType;
    private final Side side;
    private final Quantity quantity;
    private final Price price;
    private final IdempotencyKey idempotencyKey;
    private final Instant createdAt;
    private OrderState state;

    public Order(
            String id,
            Market market,
            OrderType orderType,
            Side side,
            Quantity quantity,
            Price price,
            IdempotencyKey idempotencyKey,
            Instant createdAt,
            OrderState state
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (market == null || orderType == null || side == null || quantity == null || price == null
                || idempotencyKey == null || createdAt == null || state == null) {
            throw new IllegalArgumentException("order fields must not be null");
        }
        this.id = id;
        this.market = market;
        this.orderType = orderType;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.state = state;
    }

    public static Order create(Market market, Side side, BigDecimal quantity, Money price, Instant createdAt) {
        return new Order(
                UUID.randomUUID().toString(),
                market,
                OrderType.LIMIT,
                side,
                Quantity.of(quantity),
                Price.of(price.amount(), price.currency()),
                IdempotencyKey.random(),
                createdAt,
                OrderState.NEW
        );
    }

    public void submit() {
        this.state = OrderState.SENT;
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

    public OrderType orderType() {
        return orderType;
    }

    public Side side() {
        return side;
    }

    public BigDecimal quantity() {
        return quantity.value();
    }

    public Money price() {
        return Money.of(price.value(), price.unitCurrency());
    }

    public IdempotencyKey idempotencyKey() {
        return idempotencyKey;
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
