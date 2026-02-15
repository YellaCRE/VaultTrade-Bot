package com.vaulttradebot.domain.trading;

import com.vaulttradebot.domain.trading.vo.OrderDecisionType;

import java.util.Optional;

/** Decision result with optional command payload. */
public record OrderActionDecision(
        OrderDecisionType type,
        Optional<OrderCommand> command,
        String reason
) {
    public OrderActionDecision {
        if (type == null || command == null || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("decision fields must not be null or blank");
        }
        if (type == OrderDecisionType.HOLD && command.isPresent()) {
            throw new IllegalArgumentException("HOLD must not carry command");
        }
        if (type != OrderDecisionType.HOLD && command.isEmpty()) {
            throw new IllegalArgumentException("non-HOLD must carry command");
        }
    }

    public static OrderActionDecision hold(String reason) {
        return new OrderActionDecision(OrderDecisionType.HOLD, Optional.empty(), reason);
    }

    public static OrderActionDecision place(OrderCommand command, String reason) {
        return new OrderActionDecision(OrderDecisionType.PLACE, Optional.of(command), reason);
    }

    public static OrderActionDecision modify(OrderCommand command, String reason) {
        return new OrderActionDecision(OrderDecisionType.MODIFY, Optional.of(command), reason);
    }

    public static OrderActionDecision cancel(OrderCommand command, String reason) {
        return new OrderActionDecision(OrderDecisionType.CANCEL, Optional.of(command), reason);
    }
}
