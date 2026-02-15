package com.vaulttradebot.adapter.out.upbit.mapper;

import com.vaulttradebot.adapter.out.upbit.dto.UpbitOrderResponse;
import com.vaulttradebot.domain.common.vo.Asset;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Quantity;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.ExecutionTrade;
import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.domain.execution.vo.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class UpbitOrderMapper {
    private UpbitOrderMapper() {
    }

    public static void applyExchangeState(Order order, UpbitOrderResponse response, Instant eventTime) {
        if (order == null || response == null || eventTime == null) {
            throw new IllegalArgumentException("order, response, eventTime must not be null");
        }

        String state = response.state();
        if ("wait".equalsIgnoreCase(state) && order.status() == OrderStatus.NEW) {
            order.acceptByExchange();
            return;
        }

        if ("cancel".equalsIgnoreCase(state)) {
            order.cancel();
            return;
        }

        if ("done".equalsIgnoreCase(state)) {
            if (order.status() == OrderStatus.NEW) {
                order.acceptByExchange();
            }
            BigDecimal executed = parseDecimal(response.executedVolume());
            BigDecimal remaining = parseDecimal(response.remainingVolume());
            BigDecimal nextFillQty = order.quantity().subtract(order.executedQuantity().value());
            if (remaining.signum() == 0 && nextFillQty.signum() > 0) {
                order.execute(new ExecutionTrade(
                        UUID.randomUUID().toString(),
                        Money.of(parseDecimal(response.price()), Asset.krw()),
                        Quantity.of(nextFillQty),
                        eventTime
                ));
            }
            if (executed.signum() > 0 && order.executedQuantity().value().compareTo(executed) < 0) {
                BigDecimal delta = executed.subtract(order.executedQuantity().value());
                order.execute(new ExecutionTrade(
                        UUID.randomUUID().toString(),
                        Money.of(parseDecimal(response.price()), Asset.krw()),
                        Quantity.of(delta),
                        eventTime
                ));
            }
        }
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    public static Side toSide(String upbitSide) {
        if ("bid".equalsIgnoreCase(upbitSide)) {
            return Side.BUY;
        }
        if ("ask".equalsIgnoreCase(upbitSide)) {
            return Side.SELL;
        }
        throw new IllegalArgumentException("unknown upbit side: " + upbitSide);
    }

    public static Market toMarket(String market) {
        return Market.of(market);
    }
}
