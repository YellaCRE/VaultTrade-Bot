package com.vaulttradebot.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.config.VaultTradingProperties;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.domain.execution.vo.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaperExchangeTradingAdapterTest {

    @Test
    void placeOrderBindsExchangeIdAndFillsImmediatelyByDefault() {
        // Default paper trading should behave like an immediately accepted and fully filled order.
        ClockPort clock = () -> Instant.parse("2026-03-27T12:00:00Z");
        PaperExchangeTradingAdapter adapter = new PaperExchangeTradingAdapter(clock, tradingProperties(true, "0", "0"));
        Order order = Order.create(
                Market.of("KRW-BTC"),
                Side.BUY,
                new BigDecimal("0.01000000"),
                Money.krw(new BigDecimal("50000000")),
                clock.now()
        );

        Order placed = adapter.placeOrder(order);

        assertThat(placed.exchangeOrderId()).startsWith("paper-");
        assertThat(placed.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(placed.executedQuantity().value()).isEqualByComparingTo("0.01000000");
        assertThat(placed.executedAmount().amount()).isEqualByComparingTo("500000");
        assertThat(placed.executedFee().amount()).isEqualByComparingTo("0");
    }

    @Test
    void refreshOrderFillsRemainingQuantityWhenPlaceFillIsDeferred() {
        // Deferred paper fills should remain OPEN on placement and complete during the next refresh cycle.
        ClockPort clock = new SequenceClock(
                Instant.parse("2026-03-27T12:00:00Z"),
                Instant.parse("2026-03-27T12:01:00Z")
        );
        PaperExchangeTradingAdapter adapter = new PaperExchangeTradingAdapter(clock, tradingProperties(false, "0.001", "10"));
        Order order = Order.create(
                Market.of("KRW-BTC"),
                Side.BUY,
                new BigDecimal("0.01000000"),
                Money.krw(new BigDecimal("50000000")),
                Instant.parse("2026-03-27T11:59:00Z")
        );

        Order placed = adapter.placeOrder(order);

        assertThat(placed.exchangeOrderId()).startsWith("paper-");
        assertThat(placed.status()).isEqualTo(OrderStatus.OPEN);
        assertThat(placed.executedQuantity().value()).isEqualByComparingTo("0.00000000");

        Order refreshed = adapter.refreshOrder(placed);

        assertThat(refreshed.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(refreshed.executedQuantity().value()).isEqualByComparingTo("0.01000000");
        assertThat(refreshed.executedAmount().amount()).isEqualByComparingTo("500500");
        assertThat(refreshed.executedFee().amount()).isEqualByComparingTo("501");
    }

    private VaultTradingProperties tradingProperties(boolean fillOnPlace, String feeRate, String slippageBps) {
        // Keep test setup explicit so each scenario controls fill timing and execution cost assumptions.
        VaultTradingProperties properties = new VaultTradingProperties();
        properties.getPaper().setFillOnPlace(fillOnPlace);
        properties.getPaper().setFeeRate(new BigDecimal(feeRate));
        properties.getPaper().setSlippageBps(new BigDecimal(slippageBps));
        return properties;
    }

    private static final class SequenceClock implements ClockPort {
        private final Instant[] values;
        private int index;

        private SequenceClock(Instant... values) {
            this.values = values;
        }

        @Override
        public Instant now() {
            // Return deterministic timestamps in call order so fill events can be asserted without time flakiness.
            if (index >= values.length) {
                return values[values.length - 1];
            }
            return values[index++];
        }
    }
}
