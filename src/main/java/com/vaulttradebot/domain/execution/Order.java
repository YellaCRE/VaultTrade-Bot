package com.vaulttradebot.domain.execution;

import com.vaulttradebot.domain.common.vo.IdempotencyKey;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Price;
import com.vaulttradebot.domain.common.vo.Quantity;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.event.*;
import com.vaulttradebot.domain.execution.vo.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Order {
    private final OrderId id;
    private final Market market;
    private final OrderType orderType;
    private final Side side;
    private final Quantity quantity;
    private final Price price;
    private final Money minimumProfitPrice;
    private final StrategyId strategyId;
    private final IdempotencyKey idempotencyKey;
    private final Instant createdAt;

    private OrderStatus status;
    private Quantity executedQuantity;
    private Money executedAmount;
    private long version;

    private final List<ExecutionTrade> trades;
    private final List<OrderDomainEvent> domainEvents;

    private Order(
            OrderId id,
            Market market,
            OrderType orderType,
            Side side,
            Quantity quantity,
            Price price,
            Money minimumProfitPrice,
            StrategyId strategyId,
            IdempotencyKey idempotencyKey,
            Instant createdAt
    ) {
        if (id == null || market == null || orderType == null || side == null || quantity == null || price == null
                || strategyId == null || idempotencyKey == null || createdAt == null) {
            throw new IllegalArgumentException("order fields must not be null");
        }
        if (quantity.value().signum() == 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }

        Money orderPrice = Money.of(price.value(), price.unitCurrency());
        if (minimumProfitPrice != null && minimumProfitPrice.amount().compareTo(orderPrice.amount()) > 0) {
            throw new IllegalArgumentException("order price is below minimum profit protection price");
        }

        this.id = id;
        this.market = market;
        this.orderType = orderType;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.minimumProfitPrice = minimumProfitPrice;
        this.strategyId = strategyId;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;

        this.status = OrderStatus.NEW;
        this.executedQuantity = Quantity.of(BigDecimal.ZERO);
        this.executedAmount = Money.of(BigDecimal.ZERO, price.unitCurrency());
        this.version = 0L;
        this.trades = new ArrayList<>();
        this.domainEvents = new ArrayList<>();
        this.domainEvents.add(new OrderCreated(id.value(), createdAt));
    }

    /** Creates a limit order with default strategy context. */
    public static Order create(Market market, Side side, BigDecimal quantity, Money price, Instant createdAt) {
        return create(
                market,
                side,
                quantity,
                price,
                createdAt,
                StrategyId.unassigned(),
                null
        );
    }

    /** Creates a new order aggregate and initializes lifecycle state. */
    public static Order create(
            Market market,
            Side side,
            BigDecimal quantity,
            Money price,
            Instant createdAt,
            StrategyId strategyId,
            Money minimumProfitPrice
    ) {
        return new Order(
                OrderId.random(),
                market,
                OrderType.LIMIT,
                side,
                Quantity.of(quantity),
                Price.of(price.amount(), price.currency()),
                minimumProfitPrice,
                strategyId,
                IdempotencyKey.random(),
                createdAt
        );
    }

    /** Transitions the order from NEW to OPEN after exchange acceptance. */
    public void acceptByExchange() {
        ensureStatus(OrderStatus.NEW);
        this.status = OrderStatus.OPEN;
        bumpVersion();
    }

    /** Applies one fill trade and updates execution totals and status. */
    public void execute(ExecutionTrade trade) {
        if (trade == null) {
            throw new IllegalArgumentException("trade must not be null");
        }
        if (status == OrderStatus.CANCELED || status == OrderStatus.CANCEL_REQUESTED || status == OrderStatus.REJECTED) {
            throw new IllegalStateException("cannot execute order in status: " + status);
        }
        if (status == OrderStatus.FILLED) {
            throw new IllegalStateException("order already filled");
        }
        if (status == OrderStatus.NEW) {
            throw new IllegalStateException("order has not been accepted by exchange yet");
        }

        Quantity nextExecutedQuantity = this.executedQuantity.add(trade.quantity());
        if (nextExecutedQuantity.value().compareTo(this.quantity.value()) > 0) {
            throw new IllegalStateException("executed quantity cannot exceed order quantity");
        }

        this.trades.add(trade);
        this.executedQuantity = nextExecutedQuantity;
        this.executedAmount = this.executedAmount.add(trade.executedAmount());

        if (this.executedQuantity.value().compareTo(this.quantity.value()) == 0) {
            this.status = OrderStatus.FILLED;
            this.domainEvents.add(new OrderFilled(id.value(), trade.executedAt()));
        } else {
            this.status = OrderStatus.PARTIAL_FILLED;
            this.domainEvents.add(new OrderPartiallyFilled(id.value(), trade.executedAt()));
        }
        bumpVersion();
    }

    /** Returns whether cancellation is currently allowed. */
    public boolean canCancel() {
        return status == OrderStatus.NEW
                || status == OrderStatus.OPEN
                || status == OrderStatus.PARTIAL_FILLED
                || status == OrderStatus.CANCEL_REQUESTED;
    }

    /** Marks the order as cancellation requested. */
    public void requestCancel() {
        if (!canCancel()) {
            throw new IllegalStateException("order cannot be canceled in status: " + status);
        }
        this.status = OrderStatus.CANCEL_REQUESTED;
        bumpVersion();
    }

    /** Finalizes cancellation and emits a cancellation event. */
    public void cancel() {
        if (!canCancel()) {
            throw new IllegalStateException("order cannot be canceled in status: " + status);
        }
        this.status = OrderStatus.CANCELED;
        this.domainEvents.add(new OrderCanceled(id.value(), Instant.now()));
        bumpVersion();
    }

    /** Marks the order as rejected when it is not in a terminal state. */
    public void reject() {
        if (status == OrderStatus.FILLED || status == OrderStatus.CANCELED) {
            throw new IllegalStateException("terminal order cannot be rejected");
        }
        this.status = OrderStatus.REJECTED;
        bumpVersion();
    }

    /** Returns pending domain events and clears the internal event queue. */
    public List<OrderDomainEvent> pullDomainEvents() {
        List<OrderDomainEvent> snapshot = List.copyOf(domainEvents);
        domainEvents.clear();
        return snapshot;
    }

    /** Returns the order identifier as a string value. */
    public String id() {
        return id.value();
    }

    public OrderId orderId() {
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

    public Quantity originalQuantity() {
        return quantity;
    }

    public Money price() {
        return Money.of(price.value(), price.unitCurrency());
    }

    public Money minimumProfitPrice() {
        return minimumProfitPrice;
    }

    public StrategyId strategyId() {
        return strategyId;
    }

    public IdempotencyKey idempotencyKey() {
        return idempotencyKey;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Returns the current lifecycle status of this order. */
    public OrderStatus status() {
        return status;
    }

    public Quantity executedQuantity() {
        return executedQuantity;
    }

    public Money executedAmount() {
        return executedAmount;
    }

    public List<ExecutionTrade> trades() {
        return List.copyOf(trades);
    }

    /** Returns the optimistic-lock style version value. */
    public long version() {
        return version;
    }

    private void ensureStatus(OrderStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("expected status " + expected + " but was " + status);
        }
    }

    private void bumpVersion() {
        this.version++;
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
