package com.vaulttradebot.adapter.out;

import com.vaulttradebot.adapter.out.upbit.UpbitTradingClient;
import com.vaulttradebot.adapter.out.upbit.dto.UpbitCreateOrderRequest;
import com.vaulttradebot.adapter.out.upbit.dto.UpbitOrderResponse;
import com.vaulttradebot.adapter.out.upbit.mapper.UpbitOrderMapper;
import com.vaulttradebot.application.port.out.ExchangeTradingPort;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.domain.execution.vo.OrderType;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "vault.trading.provider", havingValue = "upbit")
public class UpbitTradingAdapter implements ExchangeTradingPort {
    private final UpbitTradingClient tradingClient;

    public UpbitTradingAdapter(UpbitTradingClient tradingClient) {
        this.tradingClient = tradingClient;
    }

    @Override
    public Order placeOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (order.orderType() != OrderType.LIMIT) {
            throw new IllegalArgumentException("upbit trading adapter currently supports limit orders only");
        }

        // Submit the domain order to Upbit and then mirror the exchange state back into the aggregate.
        UpbitOrderResponse response = tradingClient.placeLimitOrder(new UpbitCreateOrderRequest(
                order.market().value(),
                toUpbitSide(order.side()),
                order.quantity().toPlainString(),
                order.price().amount().toPlainString(),
                "limit",
                order.idempotencyKey().value()
        ));
        if (response == null) {
            throw new IllegalStateException("upbit order response was empty");
        }

        UpbitOrderMapper.applyExchangeState(order, response, Instant.now());
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

        UpbitOrderResponse response = tradingClient.getOrder(order.exchangeOrderId());
        if (response == null) {
            throw new IllegalStateException("upbit order lookup response was empty");
        }

        UpbitOrderMapper.applyExchangeState(order, response, Instant.now());
        return order;
    }

    @Override
    public void cancelOrder(String orderId) {
        // Upbit cancel API works with the exchange UUID, so callers must pass that identifier here.
        UpbitOrderResponse response = tradingClient.cancelOrder(orderId);
        if (response == null) {
            throw new IllegalStateException("upbit cancel response was empty");
        }
    }

    private String toUpbitSide(Side side) {
        return switch (side) {
            case BUY -> "bid";
            case SELL -> "ask";
        };
    }
}
