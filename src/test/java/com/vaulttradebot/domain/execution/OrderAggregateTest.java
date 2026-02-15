package com.vaulttradebot.domain.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.event.OrderCreated;
import com.vaulttradebot.domain.execution.event.OrderDomainEvent;
import com.vaulttradebot.domain.execution.vo.OrderStatus;
import com.vaulttradebot.domain.execution.vo.StrategyId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderAggregateTest {

    @Test
    void createInitializesAggregateStateAndCreatedEvent() {
        // Verifies order creation sets initial lifecycle state and emits one OrderCreated event.
        Instant createdAt = Instant.parse("2026-02-15T10:00:00Z");

        Order order = Order.create(
                Market.of("KRW-BTC"),
                Side.BUY,
                new BigDecimal("0.01000000"),
                Money.krw(new BigDecimal("50000000")),
                createdAt,
                StrategyId.unassigned(),
                null
        );

        assertThat(order.status()).isEqualTo(OrderStatus.NEW);
        assertThat(order.executedQuantity().value()).isEqualByComparingTo("0.00000000");
        assertThat(order.executedAmount().amount()).isEqualByComparingTo("0");
        assertThat(order.createdAt()).isEqualTo(createdAt);

        List<OrderDomainEvent> events = order.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(OrderCreated.class);

        OrderCreated created = (OrderCreated) events.getFirst();
        assertThat(created.orderId()).isEqualTo(order.id());
        assertThat(created.occurredAt()).isEqualTo(createdAt);
        assertThat(order.pullDomainEvents()).isEmpty();
    }

    @Test
    void createRejectsZeroQuantity() {
        // Verifies aggregate invariant rejects non-positive quantity during creation.
        assertThatThrownBy(() -> Order.create(
                Market.of("KRW-BTC"),
                Side.BUY,
                BigDecimal.ZERO,
                Money.krw(new BigDecimal("50000000")),
                Instant.parse("2026-02-15T10:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity must be positive");
    }

    @Test
    void createRejectsPriceBelowMinimumProfitProtection() {
        // Verifies order price cannot be lower than configured minimum profit protection price.
        assertThatThrownBy(() -> Order.create(
                Market.of("KRW-BTC"),
                Side.SELL,
                new BigDecimal("0.01000000"),
                Money.krw(new BigDecimal("50000000")),
                Instant.parse("2026-02-15T10:00:00Z"),
                StrategyId.unassigned(),
                Money.krw(new BigDecimal("51000000"))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("below minimum profit protection price");
    }
}
