package com.vaulttradebot.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaulttradebot.application.outbox.OutboxMessage;
import com.vaulttradebot.application.port.out.ExchangeTradingPort;
import com.vaulttradebot.application.port.out.OrderRepository;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.domain.execution.vo.StrategyId;
import com.vaulttradebot.domain.trading.OrderCommand;
import com.vaulttradebot.domain.trading.vo.OrderCommandType;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class OrderCommandExecutionService {
    private final ExchangeTradingPort exchangeTradingPort;
    private final OrderRepository orderRepository;
    private final OrderPersistenceService orderPersistenceService;
    private final ObjectMapper objectMapper;

    public OrderCommandExecutionService(
            ExchangeTradingPort exchangeTradingPort,
            OrderRepository orderRepository,
            OrderPersistenceService orderPersistenceService,
            ObjectMapper objectMapper
    ) {
        this.exchangeTradingPort = exchangeTradingPort;
        this.orderRepository = orderRepository;
        this.orderPersistenceService = orderPersistenceService;
        this.objectMapper = objectMapper;
    }

    public void execute(OutboxMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        if (!"OrderCommandRequested".equals(message.eventType())) {
            return;
        }

        OrderCommandRequestedPayload payload = parsePayload(message.payload());
        OrderCommand command = toCommand(payload);
        switch (command.type()) {
            case CREATE -> executeCreate(command, payload);
            case REPLACE -> executeReplace(command, payload);
            case CANCEL -> executeCancel(command);
            default -> throw new IllegalStateException("unsupported order command type: " + command.type());
        }
    }

    private void executeCreate(OrderCommand command, OrderCommandRequestedPayload payload) {
        Order created = Order.create(
                command.market(),
                command.side(),
                command.quantity(),
                command.price(),
                payload.occurredAt(),
                StrategyId.of(payload.strategyId()),
                null
        );
        Order placed = exchangeTradingPort.placeOrder(created);
        orderPersistenceService.persist(placed);
    }

    private void executeReplace(OrderCommand command, OrderCommandRequestedPayload payload) {
        executeCancel(OrderCommand.cancel(command.targetOrderId(), command.reason()));
        executeCreate(OrderCommand.create(
                command.market(),
                command.side(),
                command.price(),
                command.quantity(),
                command.clientOrderId(),
                command.reason()
        ), payload);
    }

    private void executeCancel(OrderCommand command) {
        exchangeTradingPort.cancelOrder(command.targetOrderId());
        Order existing = findOrder(command.targetOrderId())
                .orElseThrow(() -> new IllegalStateException("order not found for cancel: " + command.targetOrderId()));
        if (existing.canCancel()) {
            existing.requestCancel();
            existing.cancel();
            orderPersistenceService.persist(existing);
        }
    }

    private Optional<Order> findOrder(String orderId) {
        return orderRepository.findAll().stream()
                .filter(order -> order.id().equals(orderId))
                .findFirst();
    }

    private OrderCommandRequestedPayload parsePayload(String payload) {
        try {
            return objectMapper.readValue(payload, OrderCommandRequestedPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to parse OrderCommandRequested payload", e);
        }
    }

    private OrderCommand toCommand(OrderCommandRequestedPayload payload) {
        OrderCommandType type = OrderCommandType.valueOf(payload.commandType());
        return switch (type) {
            case CREATE -> OrderCommand.create(
                    Market.of(payload.market()),
                    Side.valueOf(payload.side()),
                    Money.krw(new BigDecimal(payload.price())),
                    new BigDecimal(payload.quantity()),
                    payload.clientOrderId(),
                    payload.reason()
            );
            case REPLACE -> OrderCommand.replace(
                    payload.targetOrderId(),
                    Market.of(payload.market()),
                    Side.valueOf(payload.side()),
                    Money.krw(new BigDecimal(payload.price())),
                    new BigDecimal(payload.quantity()),
                    payload.clientOrderId(),
                    payload.reason()
            );
            case CANCEL -> OrderCommand.cancel(payload.targetOrderId(), payload.reason());
        };
    }

    private record OrderCommandRequestedPayload(
            String cycleId,
            String strategyId,
            String dataTimestamp,
            String decision,
            String reason,
            String commandType,
            String targetOrderId,
            String market,
            String side,
            String orderType,
            String price,
            String quantity,
            String clientOrderId
    ) {
        java.time.Instant occurredAt() {
            return java.time.Instant.parse(dataTimestamp);
        }
    }
}
