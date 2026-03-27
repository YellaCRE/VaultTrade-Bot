package com.vaulttradebot.domain.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.vo.OrderType;
import com.vaulttradebot.domain.trading.vo.OrderCommandType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderCommandTest {

    @Test
    void createFactoryMapsRequestToCreateCommand() {
        // Verifies create factory converts request fields into a CREATE command with LIMIT type.
        OrderCommand command = OrderCommand.create(
                Market.of("KRW-BTC"),
                Side.BUY,
                Money.krw(new BigDecimal("50000000")),
                new BigDecimal("0.00200000"),
                "client-order-1",
                "new signal"
        );

        assertThat(command.type()).isEqualTo(OrderCommandType.CREATE);
        assertThat(command.targetOrderId()).isNull();
        assertThat(command.orderType()).isEqualTo(OrderType.LIMIT);
        assertThat(command.market()).isEqualTo(Market.of("KRW-BTC"));
        assertThat(command.side()).isEqualTo(Side.BUY);
        assertThat(command.quantity()).isEqualByComparingTo("0.00200000");
    }

    @Test
    void replaceFactoryRequiresTargetOrderId() {
        // Verifies replace command keeps target id and uses CREATE-like payload fields.
        OrderCommand command = OrderCommand.replace(
                "open-order-1",
                Market.of("KRW-BTC"),
                Side.SELL,
                Money.krw(new BigDecimal("50000000")),
                new BigDecimal("0.00100000"),
                "client-order-2",
                "price changed"
        );

        assertThat(command.type()).isEqualTo(OrderCommandType.REPLACE);
        assertThat(command.targetOrderId()).isEqualTo("open-order-1");
        assertThat(command.side()).isEqualTo(Side.SELL);
    }

    @Test
    void cancelFactoryRejectsBlankTargetOrderId() {
        // Verifies cancel command is rejected when target order id is missing.
        assertThatThrownBy(() -> OrderCommand.cancel("", "risk rejected"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetOrderId");
    }

    @Test
    void createRejectsInvalidQuantityAndClientOrderId() {
        // Verifies command invariants reject zero quantity and blank client order id.
        assertThatThrownBy(() -> OrderCommand.create(
                Market.of("KRW-BTC"),
                Side.BUY,
                Money.krw(new BigDecimal("50000000")),
                BigDecimal.ZERO,
                "client-order-3",
                "new signal"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity must be positive");

        assertThatThrownBy(() -> OrderCommand.create(
                Market.of("KRW-BTC"),
                Side.BUY,
                Money.krw(new BigDecimal("50000000")),
                new BigDecimal("0.00100000"),
                " ",
                "new signal"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clientOrderId");
    }
}
