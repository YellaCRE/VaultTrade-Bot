package com.vaulttradebot.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaulttradebot.adapter.out.upbit.UpbitTradingClient;
import com.vaulttradebot.adapter.out.upbit.dto.UpbitOrderResponse;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.domain.execution.vo.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpbitTradingAdapterTest {
    @Mock
    private UpbitTradingClient tradingClient;

    @Test
    void placeOrderMapsWaitStateToOpenOrder() {
        // A newly accepted Upbit order should move the aggregate from NEW to OPEN without fills.
        Order order = Order.create(
                Market.of("KRW-BTC"),
                Side.BUY,
                new BigDecimal("0.01"),
                Money.krw(new BigDecimal("50000000")),
                Instant.parse("2026-03-15T00:00:00Z")
        );
        when(tradingClient.placeLimitOrder(any()))
                .thenReturn(new UpbitOrderResponse(
                        "upbit-uuid",
                        order.idempotencyKey().value(),
                        "wait",
                        "bid",
                        "KRW-BTC",
                        "50000000",
                        "0.01",
                        "0"
                ));

        UpbitTradingAdapter adapter = new UpbitTradingAdapter(tradingClient);
        Order placed = adapter.placeOrder(order);

        assertThat(placed.status()).isEqualTo(OrderStatus.OPEN);
        assertThat(placed.exchangeOrderId()).isEqualTo("upbit-uuid");
        verify(tradingClient).placeLimitOrder(any());
    }

    @Test
    void placeOrderMapsDoneStateToFilledOrder() {
        // A fully executed Upbit response should translate into a filled aggregate with executed quantity.
        Order order = Order.create(
                Market.of("KRW-BTC"),
                Side.SELL,
                new BigDecimal("0.02"),
                Money.krw(new BigDecimal("51000000")),
                Instant.parse("2026-03-15T00:00:00Z")
        );
        when(tradingClient.placeLimitOrder(any()))
                .thenReturn(new UpbitOrderResponse(
                        "upbit-uuid",
                        order.idempotencyKey().value(),
                        "done",
                        "ask",
                        "KRW-BTC",
                        "51000000",
                        "0",
                        "0.02"
                ));

        UpbitTradingAdapter adapter = new UpbitTradingAdapter(tradingClient);
        Order placed = adapter.placeOrder(order);

        assertThat(placed.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(placed.exchangeOrderId()).isEqualTo("upbit-uuid");
        assertThat(placed.executedQuantity().value()).isEqualByComparingTo("0.02");
    }

    @Test
    void rejectsUnsupportedMarketOrders() {
        // The adapter is intentionally scoped to limit orders until market-order handling is defined.
        Order marketOrder = Order.rehydrate(
                com.vaulttradebot.domain.execution.vo.OrderId.random(),
                Market.of("KRW-BTC"),
                com.vaulttradebot.domain.execution.vo.OrderType.MARKET,
                Side.BUY,
                com.vaulttradebot.domain.common.vo.Quantity.of(new BigDecimal("0.01")),
                com.vaulttradebot.domain.common.vo.Price.of(new BigDecimal("50000000"), com.vaulttradebot.domain.common.vo.Asset.krw()),
                null,
                com.vaulttradebot.domain.execution.vo.StrategyId.unassigned(),
                com.vaulttradebot.domain.common.vo.IdempotencyKey.random(),
                Instant.parse("2026-03-15T00:00:00Z"),
                OrderStatus.NEW,
                com.vaulttradebot.domain.common.vo.Quantity.of(BigDecimal.ZERO),
                Money.krw(BigDecimal.ZERO),
                null,
                0L
        );

        UpbitTradingAdapter adapter = new UpbitTradingAdapter(tradingClient);

        assertThatThrownBy(() -> adapter.placeOrder(marketOrder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit orders only");
    }
}
