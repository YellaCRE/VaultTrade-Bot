package com.vaulttradebot.domain.trading;

import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.vo.OrderType;
import com.vaulttradebot.domain.trading.vo.OrderCommandType;

import java.math.BigDecimal;

/** Executable command that can be handled by upper layers. */
public record OrderCommand(
        OrderCommandType type,
        String targetOrderId,
        Market market,
        Side side,
        OrderType orderType,
        Money price,
        BigDecimal quantity,
        String clientOrderId,
        String reason
) {
    public OrderCommand {
        if (type == null || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("type and reason must not be null or blank");
        }
        if (type == OrderCommandType.CANCEL) {
            if (targetOrderId == null || targetOrderId.isBlank()) {
                throw new IllegalArgumentException("cancel command requires targetOrderId");
            }
        } else {
            if (market == null || side == null || orderType == null || price == null || quantity == null) {
                throw new IllegalArgumentException("create/replace command fields must not be null");
            }
            if (quantity.signum() <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            if (clientOrderId == null || clientOrderId.isBlank()) {
                throw new IllegalArgumentException("clientOrderId must not be blank");
            }
        }
    }

    public static OrderCommand create(
            Market market,
            Side side,
            Money price,
            BigDecimal quantity,
            String clientOrderId,
            String reason
    ) {
        return new OrderCommand(
                OrderCommandType.CREATE,
                null,
                market,
                side,
                OrderType.LIMIT,
                price,
                quantity,
                clientOrderId,
                reason
        );
    }

    public static OrderCommand replace(
            String targetOrderId,
            Market market,
            Side side,
            Money price,
            BigDecimal quantity,
            String clientOrderId,
            String reason
    ) {
        return new OrderCommand(
                OrderCommandType.REPLACE,
                targetOrderId,
                market,
                side,
                OrderType.LIMIT,
                price,
                quantity,
                clientOrderId,
                reason
        );
    }

    public static OrderCommand cancel(String targetOrderId, String reason) {
        return new OrderCommand(
                OrderCommandType.CANCEL,
                targetOrderId,
                null,
                null,
                null,
                null,
                null,
                null,
                reason
        );
    }
}
