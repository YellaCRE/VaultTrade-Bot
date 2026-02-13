package com.vaulttradebot.domain.trading;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaulttradebot.domain.shared.Market;
import com.vaulttradebot.domain.shared.Money;
import com.vaulttradebot.domain.shared.Side;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderDecisionServiceTest {
    private final OrderDecisionService service = new OrderDecisionService();
    private static final Currency KRW = Currency.getInstance("KRW");

    @Test
    void returnsEmptyForHoldSignal() {
        TradingSignal signal = new TradingSignal(SignalAction.HOLD, "no signal");

        Optional<OrderDecision> decision = service.decide(
                signal,
                new Market("KRW-BTC", "BTC", "KRW"),
                Money.of(new BigDecimal("50000000"), KRW),
                new BigDecimal("100000")
        );

        assertThat(decision).isEmpty();
    }

    @Test
    void calculatesQuantityFromMaxOrderAmount() {
        TradingSignal signal = new TradingSignal(SignalAction.BUY, "entry");

        OrderDecision decision = service.decide(
                signal,
                new Market("KRW-BTC", "BTC", "KRW"),
                Money.of(new BigDecimal("50000000"), KRW),
                new BigDecimal("100000")
        ).orElseThrow();

        assertThat(decision.side()).isEqualTo(Side.BUY);
        assertThat(decision.quantity()).isEqualByComparingTo(new BigDecimal("0.00200000"));
    }
}
