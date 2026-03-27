package com.vaulttradebot.application.usecase;

import com.vaulttradebot.application.outbox.OutboxMessage;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.port.out.OrderOutboxTransactionPort;
import com.vaulttradebot.application.port.out.OrderRepository;
import com.vaulttradebot.application.port.out.OutboxRepository;
import com.vaulttradebot.application.port.out.OutboxPayloadSerializer;
import com.vaulttradebot.application.port.out.PortfolioRepository;
import com.vaulttradebot.domain.common.vo.Asset;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Quantity;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.domain.execution.event.OrderDomainEvent;
import com.vaulttradebot.domain.portfolio.Position;
import com.vaulttradebot.domain.portfolio.event.BuyFilled;
import com.vaulttradebot.domain.portfolio.event.SellFilled;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class OrderPersistenceService {
    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final PortfolioRepository portfolioRepository;
    private final OrderOutboxTransactionPort transactionPort;
    private final ClockPort clockPort;
    private final OutboxPayloadSerializer outboxPayloadSerializer;

    public OrderPersistenceService(
            OrderRepository orderRepository,
            OutboxRepository outboxRepository,
            PortfolioRepository portfolioRepository,
            OrderOutboxTransactionPort transactionPort,
            ClockPort clockPort,
            OutboxPayloadSerializer outboxPayloadSerializer
    ) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.portfolioRepository = portfolioRepository;
        this.transactionPort = transactionPort;
        this.clockPort = clockPort;
        this.outboxPayloadSerializer = outboxPayloadSerializer;
    }

    /** Persists order and outbox messages in one atomic transaction boundary. */
    public Order persist(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        List<OrderDomainEvent> domainEvents = order.pullDomainEvents();
        Instant now = clockPort.now();
        Optional<Order> previousOrder = findExistingOrder(order.id());
        try {
            transactionPort.execute(() -> {
                orderRepository.save(order);
                updatePortfolioPosition(order, previousOrder);
                for (OrderDomainEvent event : domainEvents) {
                    outboxRepository.save(OutboxMessage.fromOrderEvent(
                            event,
                            outboxPayloadSerializer.serialize(event),
                            outboxPayloadSerializer.payloadVersion(),
                            now
                    ));
                }
            });
        } catch (RuntimeException e) {
            order.restoreDomainEvents(domainEvents);
            throw e;
        }
        return order;
    }

    private void updatePortfolioPosition(Order order, Optional<Order> previousOrder) {
        BigDecimal previousExecutedQty = previousOrder.map(existing -> existing.executedQuantity().value()).orElse(BigDecimal.ZERO);
        BigDecimal previousExecutedAmount = previousOrder.map(existing -> existing.executedAmount().amount()).orElse(BigDecimal.ZERO);

        BigDecimal deltaQuantity = order.executedQuantity().value().subtract(previousExecutedQty);
        BigDecimal deltaAmount = order.executedAmount().amount().subtract(previousExecutedAmount);
        if (deltaQuantity.signum() == 0) {
            return;
        }
        if (deltaQuantity.signum() < 0 || deltaAmount.signum() < 0) {
            throw new IllegalStateException("executed order state cannot move backwards");
        }

        Optional<Position> existingPosition = portfolioRepository.findByMarket(order.market().value());
        Position currentPosition = existingPosition
                .orElseGet(() -> Position.open(order.market(), order.createdAt()));
        long expectedVersion = existingPosition.map(Position::version).orElse(-1L);
        Money executionPrice = Money.of(
                deltaAmount.divide(deltaQuantity, 8, RoundingMode.HALF_UP),
                Asset.krw()
        );
        Quantity fillQuantity = Quantity.of(deltaQuantity);
        Instant occurredAt = order.trades().isEmpty()
                ? order.createdAt()
                : order.trades().getLast().executedAt();

        if (order.side() == Side.BUY) {
            currentPosition.apply(new BuyFilled(order.market(), fillQuantity, executionPrice, Money.krw(BigDecimal.ZERO), occurredAt));
        } else {
            currentPosition.apply(new SellFilled(order.market(), fillQuantity, executionPrice, Money.krw(BigDecimal.ZERO), occurredAt));
        }
        portfolioRepository.save(currentPosition, expectedVersion);
    }

    private Optional<Order> findExistingOrder(String orderId) {
        return orderRepository.findById(orderId);
    }
}
