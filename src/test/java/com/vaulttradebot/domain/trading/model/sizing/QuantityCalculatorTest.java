package com.vaulttradebot.domain.trading.model.sizing;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.vo.OrderType;
import com.vaulttradebot.domain.trading.model.sizing.snapshot.AccountSnapshot;
import com.vaulttradebot.domain.trading.model.sizing.snapshot.ExecutionSnapshot;
import com.vaulttradebot.domain.trading.model.sizing.vo.ExchangeConstraints;
import com.vaulttradebot.domain.trading.model.sizing.vo.QuantityCalculationRequest;
import com.vaulttradebot.domain.trading.model.sizing.vo.QuantityCalculationResult;
import com.vaulttradebot.domain.trading.model.sizing.vo.RiskCaps;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class QuantityCalculatorTest {
    private final QuantityCalculator calculator = new QuantityCalculator();

    @Test
    void quantizesLimitQuantityDownByStep() {
        // Verifies limit quantity is rounded down to quantityStep deterministically.
        QuantityCalculationResult result = calculator.calculate(new QuantityCalculationRequest(
                Side.BUY,
                OrderType.LIMIT,
                1.0,
                new BigDecimal("100"),
                Money.krw(new BigDecimal("30000")),
                exchange(new BigDecimal("0"), new BigDecimal("0.0001"), new BigDecimal("10"), new BigDecimal("0.001")),
                riskCaps(),
                execution(Money.krw(new BigDecimal("30000")), null, null),
                account()
        ));

        assertThat(result.isTradable()).isTrue();
        assertThat(result.quantity()).isEqualByComparingTo("0.00300000");
    }

    @Test
    void marketBuyUsesBestAskWithSlippageBuffer() {
        // Verifies market-buy reference price uses ask price plus configured slippage buffer.
        QuantityCalculationResult result = calculator.calculate(new QuantityCalculationRequest(
                Side.BUY,
                OrderType.MARKET,
                1.0,
                new BigDecimal("100"),
                Money.krw(new BigDecimal("1")),
                exchange(new BigDecimal("0"), new BigDecimal("0.0001"), new BigDecimal("10"), new BigDecimal("0.0001")),
                riskCaps(),
                execution(
                        Money.krw(new BigDecimal("10000")),
                        Money.krw(new BigDecimal("9990")),
                        Money.krw(new BigDecimal("10010"))
                ),
                account()
        ));

        assertThat(result.isTradable()).isTrue();
        assertThat(result.quantity()).isEqualByComparingTo("0.00990000");
    }

    @Test
    void stepsUpToMeetMinNotionalWhenEnabled() {
        // Verifies calculator can step up quantity to satisfy min notional when policy allows it.
        QuantityCalculationResult result = calculator.calculate(new QuantityCalculationRequest(
                Side.BUY,
                OrderType.LIMIT,
                1.0,
                new BigDecimal("50"),
                Money.krw(new BigDecimal("10000")),
                exchange(new BigDecimal("100"), new BigDecimal("0.001"), new BigDecimal("10"), new BigDecimal("0.001")),
                riskCaps(),
                execution(Money.krw(new BigDecimal("10000")), null, null),
                account()
        ));

        assertThat(result.isTradable()).isTrue();
        assertThat(result.quantity()).isEqualByComparingTo("0.01000000");
    }

    @Test
    void rejectsBelowMinNotionalWhenStepUpDisabled() {
        // Verifies below-min-notional order is rejected when step-up policy is disabled.
        QuantityCalculationResult result = calculator.calculate(new QuantityCalculationRequest(
                Side.BUY,
                OrderType.LIMIT,
                1.0,
                new BigDecimal("50"),
                Money.krw(new BigDecimal("10000")),
                exchange(new BigDecimal("100"), new BigDecimal("0.001"), new BigDecimal("10"), new BigDecimal("0.001")),
                new RiskCaps(
                        new BigDecimal("1000000"),
                        new BigDecimal("100"),
                        new BigDecimal("1.0"),
                        false
                ),
                execution(Money.krw(new BigDecimal("10000")), null, null),
                account()
        ));

        assertThat(result.isTradable()).isFalse();
        assertThat(result.holdReason()).isEqualTo("below minimum notional");
    }

    @ParameterizedTest
    @CsvSource({
            "99,false,below minimum quantity",
            "100,true,",
            "101,true,"
    })
    void respectsMinQuantityBoundary(String targetOrderKrw, boolean tradable, String holdReason) {
        // Verifies min-quantity boundary behavior for minQty-epsilon, minQty, and minQty+epsilon.
        QuantityCalculationResult result = calculator.calculate(new QuantityCalculationRequest(
                Side.BUY,
                OrderType.LIMIT,
                1.0,
                new BigDecimal(targetOrderKrw),
                Money.krw(new BigDecimal("10000")),
                exchange(new BigDecimal("0"), new BigDecimal("0.0100"), new BigDecimal("10"), new BigDecimal("0.0001")),
                riskCaps(),
                execution(Money.krw(new BigDecimal("10000")), null, null),
                account()
        ));

        assertThat(result.isTradable()).isEqualTo(tradable);
        if (tradable) {
            assertThat(result.quantity()).isGreaterThanOrEqualTo(new BigDecimal("0.01000000"));
        } else {
            assertThat(result.holdReason()).isEqualTo(holdReason);
        }
    }

    private ExchangeConstraints exchange(
            BigDecimal minNotional,
            BigDecimal minQty,
            BigDecimal maxQty,
            BigDecimal step
    ) {
        return new ExchangeConstraints(
                minNotional,
                minQty,
                maxQty,
                step,
                new BigDecimal("1")
        );
    }

    private RiskCaps riskCaps() {
        return new RiskCaps(
                new BigDecimal("1000000"),
                new BigDecimal("100"),
                new BigDecimal("1.0"),
                true
        );
    }

    private ExecutionSnapshot execution(Money lastPrice, Money bestBid, Money bestAsk) {
        return new ExecutionSnapshot(
                lastPrice,
                bestBid,
                bestAsk,
                new BigDecimal("0.0010"),
                new BigDecimal("0.0005"),
                BigDecimal.ZERO,
                BigDecimal.ONE
        );
    }

    private AccountSnapshot account() {
        return new AccountSnapshot(
                new BigDecimal("1000000"),
                new BigDecimal("10"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}
