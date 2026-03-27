package com.vaulttradebot.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Quantity;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.domain.execution.vo.ExecutionTrade;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrderResponseTest {

    @Test
    void mapsExchangeOrderIdForApiResponse() {
        // Verifies the orders API response exposes the exchange-native order id and executed fee when present.
        Order order = Order.create(
                Market.of("KRW-BTC"),
                Side.BUY,
                new BigDecimal("0.00200000"),
                Money.krw(new BigDecimal("50000000")),
                Instant.parse("2026-03-27T12:00:00Z")
        );
        order.acceptByExchange();
        order.execute(new ExecutionTrade(
                "trade-1",
                Money.krw(new BigDecimal("50000000")),
                Quantity.of(new BigDecimal("0.00100000")),
                Money.krw(new BigDecimal("25")),
                Instant.parse("2026-03-27T12:00:30Z")
        ));
        order.bindExchangeOrderId("upbit-uuid-1");

        OrderResponse response = OrderResponse.from(order);

        assertThat(response.id()).isEqualTo(order.id());
        assertThat(response.exchangeOrderId()).isEqualTo("upbit-uuid-1");
        assertThat(response.executedFeeKrw()).isEqualByComparingTo("25");
    }
}
