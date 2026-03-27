package com.vaulttradebot.domain.trading;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.common.vo.Timeframe;
import com.vaulttradebot.domain.trading.snapshot.OpenOrderSnapshot;
import com.vaulttradebot.domain.trading.model.sizing.QuantityCalculator;
import com.vaulttradebot.domain.trading.model.strategy.vo.SignalDecision;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.vaulttradebot.domain.trading.vo.*;
import org.junit.jupiter.api.Test;

class OrderDecisionServiceTest {
    private final OrderDecisionService service = new OrderDecisionService(new QuantityCalculator());
    private static final Instant NOW = Instant.parse("2026-02-15T10:00:00Z");
    private static final OrderMarketPolicy POLICY = new OrderMarketPolicy(
            new BigDecimal("1"),
            new BigDecimal("0.00000001"),
            new BigDecimal("5000"),
            new BigDecimal("0.00000001"),
            new BigDecimal("1000"),
            new BigDecimal("0.3000"),
            BigDecimal.ONE,
            true,
            new BigDecimal("0.01"),
            new BigDecimal("1"),
            new BigDecimal("1"),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10)
    );

    @Test
    void returnsEmptyForHoldSignal() {
        // Verifies HOLD signals do not produce executable order decisions.
        TradingSignal signal = new TradingSignal(SignalAction.HOLD, "no signal");

        Optional<OrderDecision> decision = service.decide(
                signal,
                Market.of("KRW-BTC"),
                Money.krw(new BigDecimal("50000000")),
                new BigDecimal("100000")
        );

        assertThat(decision).isEmpty();
    }

    @Test
    void calculatesQuantityFromMaxOrderAmount() {
        // Verifies quantity is derived from max KRW order amount and market price.
        TradingSignal signal = new TradingSignal(SignalAction.BUY, "entry");

        OrderDecision decision = service.decide(
                signal,
                Market.of("KRW-BTC"),
                Money.krw(new BigDecimal("50000000")),
                new BigDecimal("100000")
        ).orElseThrow();

        assertThat(decision.side()).isEqualTo(Side.BUY);
        assertThat(decision.quantity()).isEqualByComparingTo(new BigDecimal("0.00200000"));
    }

    @Test
    void returnsHoldWhenSignalIsHold() {
        // HOLD signal must not produce command.
        OrderActionDecision decision = service.decide(baseContext(SignalAction.HOLD, Optional.empty(), true));

        assertThat(decision.type()).isEqualTo(OrderDecisionType.HOLD);
        assertThat(decision.command()).isEmpty();
    }

    @Test
    void returnsPlaceWhenSignalIsTradable() {
        // BUY signal with valid market data should create PLACE command.
        OrderActionDecision decision = service.decide(baseContext(SignalAction.BUY, Optional.empty(), true));

        assertThat(decision.type()).isEqualTo(OrderDecisionType.PLACE);
        assertThat(decision.command()).isPresent();
        OrderCommand command = decision.command().orElseThrow();
        assertThat(command.type()).isEqualTo(OrderCommandType.CREATE);
        assertThat(command.market()).isEqualTo(Market.of("KRW-BTC"));
        assertThat(command.side()).isEqualTo(Side.BUY);
        assertThat(command.price().amount()).isEqualByComparingTo("50000000");
        assertThat(command.quantity()).isEqualByComparingTo("0.00160000");
        assertThat(command.clientOrderId()).startsWith("vtb-");
    }

    @Test
    void returnsModifyWhenDifferenceIsMeaningful() {
        // Significant difference should return REPLACE command.
        OpenOrderSnapshot openOrder = new OpenOrderSnapshot(
                "open-1",
                Market.of("KRW-BTC"),
                Side.BUY,
                Money.krw(new BigDecimal("49990000")),
                new BigDecimal("0.00100000"),
                "old-client-id",
                NOW.minusSeconds(20)
        );

        OrderActionDecision decision = service.decide(baseContext(SignalAction.BUY, Optional.of(openOrder), true));

        assertThat(decision.type()).isEqualTo(OrderDecisionType.MODIFY);
        assertThat(decision.command()).isPresent();
        OrderCommand command = decision.command().orElseThrow();
        assertThat(command.type()).isEqualTo(OrderCommandType.REPLACE);
        assertThat(command.targetOrderId()).isEqualTo("open-1");
    }

    @Test
    void returnsHoldWhenDifferenceIsWithinTolerance() {
        // Small delta inside tolerance should keep current order.
        OpenOrderSnapshot openOrder = new OpenOrderSnapshot(
                "open-1",
                Market.of("KRW-BTC"),
                Side.BUY,
                Money.krw(new BigDecimal("50000000")),
                new BigDecimal("0.00160000"),
                "different-client-id",
                NOW.minusSeconds(20)
        );

        OrderActionDecision decision = service.decide(baseContext(SignalAction.BUY, Optional.of(openOrder), true));

        assertThat(decision.type()).isEqualTo(OrderDecisionType.HOLD);
    }

    @Test
    void returnsCancelWhenRiskRejectedAndOpenOrderExists() {
        // Risk reject policy cancels existing open order.
        OpenOrderSnapshot openOrder = new OpenOrderSnapshot(
                "open-1",
                Market.of("KRW-BTC"),
                Side.BUY,
                Money.krw(new BigDecimal("50000000")),
                new BigDecimal("0.00200000"),
                "existing-id",
                NOW.minusSeconds(20)
        );

        OrderActionDecision decision = service.decide(baseContext(SignalAction.BUY, Optional.of(openOrder), false));

        assertThat(decision.type()).isEqualTo(OrderDecisionType.CANCEL);
        assertThat(decision.command().orElseThrow().type()).isEqualTo(OrderCommandType.CANCEL);
    }

    @Test
    void returnsHoldForDuplicateMarketEvent() {
        // Same market event should not emit duplicate command.
        OrderDecisionContext first = baseContext(SignalAction.BUY, Optional.empty(), true);
        OrderCommand firstCommand = service.decide(first).command().orElseThrow();

        OpenOrderSnapshot openOrder = new OpenOrderSnapshot(
                "open-1",
                first.market(),
                Side.BUY,
                firstCommand.price(),
                firstCommand.quantity(),
                firstCommand.clientOrderId(),
                NOW.minusSeconds(5)
        );
        OrderDecisionContext second = baseContext(SignalAction.BUY, Optional.of(openOrder), true);

        OrderActionDecision decision = service.decide(second);

        assertThat(decision.type()).isEqualTo(OrderDecisionType.HOLD);
    }

    @Test
    void returnsHoldWhenDataIsStaleWithoutOpenOrder() {
        // Stale market data should block new order creation.
        OrderDecisionContext staleContext = new OrderDecisionContext(
                signal(SignalAction.BUY),
                Market.of("KRW-BTC"),
                Money.krw(new BigDecimal("50000000")),
                Money.krw(new BigDecimal("49990000")),
                Money.krw(new BigDecimal("50010000")),
                NOW.minusSeconds(30),
                NOW,
                new BigDecimal("100000"),
                new BigDecimal("1"),
                new BigDecimal("1000000"),
                new BigDecimal("1"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.0005"),
                new BigDecimal("0.0020"),
                new BigDecimal("1"),
                true,
                "RISK_OK",
                Optional.empty(),
                "event-1",
                POLICY,
                NOW.minusSeconds(20)
        );

        OrderActionDecision decision = service.decide(staleContext);

        assertThat(decision.type()).isEqualTo(OrderDecisionType.HOLD);
    }

    @Test
    void returnsHoldWhenCooldownIsActive() {
        // Cooldown should block re-ordering.
        OrderDecisionContext context = new OrderDecisionContext(
                signal(SignalAction.BUY),
                Market.of("KRW-BTC"),
                Money.krw(new BigDecimal("50000000")),
                Money.krw(new BigDecimal("49990000")),
                Money.krw(new BigDecimal("50010000")),
                NOW.minusSeconds(1),
                NOW,
                new BigDecimal("100000"),
                new BigDecimal("1"),
                new BigDecimal("1000000"),
                new BigDecimal("1"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.0005"),
                new BigDecimal("0.0020"),
                new BigDecimal("1"),
                true,
                "RISK_OK",
                Optional.empty(),
                "event-1",
                POLICY,
                NOW.minusSeconds(3)
        );

        OrderActionDecision decision = service.decide(context);

        assertThat(decision.type()).isEqualTo(OrderDecisionType.HOLD);
    }

    @Test
    void returnsHoldWhenSpreadTooWide() {
        // Abnormal spread should block order.
        OrderDecisionContext context = new OrderDecisionContext(
                signal(SignalAction.BUY),
                Market.of("KRW-BTC"),
                Money.krw(new BigDecimal("50000000")),
                Money.krw(new BigDecimal("49000000")),
                Money.krw(new BigDecimal("51000000")),
                NOW.minusSeconds(1),
                NOW,
                new BigDecimal("100000"),
                new BigDecimal("1"),
                new BigDecimal("1000000"),
                new BigDecimal("1"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.0005"),
                new BigDecimal("0.0020"),
                new BigDecimal("1"),
                true,
                "RISK_OK",
                Optional.empty(),
                "event-1",
                POLICY,
                NOW.minusSeconds(20)
        );

        OrderActionDecision decision = service.decide(context);

        assertThat(decision.type()).isEqualTo(OrderDecisionType.HOLD);
    }

    private OrderDecisionContext baseContext(
            SignalAction action,
            Optional<OpenOrderSnapshot> openOrder,
            boolean riskAllowed
    ) {
        return new OrderDecisionContext(
                signal(action),
                Market.of("KRW-BTC"),
                Money.krw(new BigDecimal("50000000")),
                Money.krw(new BigDecimal("49990000")),
                Money.krw(new BigDecimal("50010000")),
                NOW.minusSeconds(1),
                NOW,
                new BigDecimal("100000"),
                new BigDecimal("1"),
                new BigDecimal("1000000"),
                new BigDecimal("1"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.0005"),
                new BigDecimal("0.0020"),
                new BigDecimal("1"),
                riskAllowed,
                riskAllowed ? "RISK_OK" : "RISK_DENIED",
                openOrder,
                "event-1",
                POLICY,
                NOW.minusSeconds(20)
        );
    }

    private SignalDecision signal(SignalAction action) {
        return new SignalDecision(
                action,
                action == SignalAction.HOLD ? 0.0 : 0.8,
                "test-signal",
                NOW,
                "KRW-BTC",
                Timeframe.M1
        );
    }
}
