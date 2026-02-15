package com.vaulttradebot.domain.trading;

import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.risk.RiskPolicy;
import com.vaulttradebot.domain.trading.strategy.vo.SignalDecision;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import com.vaulttradebot.domain.trading.vo.SignalAction;
import com.vaulttradebot.domain.trading.vo.TradingSignal;
import org.springframework.stereotype.Component;

@Component
/** Converts validated signals into executable order intents. */
public class OrderDecisionService {
    /** Builds an order candidate from a legacy trading signal and order budget. */
    public Optional<OrderDecision> decide(
            TradingSignal signal,
            Market market,
            Money lastPrice,
            BigDecimal maxOrderKrw
    ) {
        if (signal == null || market == null || lastPrice == null || maxOrderKrw == null) {
            throw new IllegalArgumentException("inputs must not be null");
        }
        if (signal.action() == SignalAction.HOLD) {
            return Optional.empty();
        }
        if (lastPrice.amount().signum() <= 0 || maxOrderKrw.signum() <= 0) {
            return Optional.empty();
        }

        BigDecimal quantity = maxOrderKrw
                .divide(lastPrice.amount(), 8, RoundingMode.DOWN);
        if (quantity.signum() <= 0) {
            return Optional.empty();
        }

        Side side = signal.action() == SignalAction.BUY ? Side.BUY : Side.SELL;
        return Optional.of(new OrderDecision(market, side, quantity, lastPrice, signal.reason()));
    }

    /** Builds an order candidate from strategy output and order budget. */
    public Optional<OrderDecision> decide(
            SignalDecision signal,
            Market market,
            Money lastPrice,
            BigDecimal maxOrderKrw
    ) {
        if (signal == null) {
            throw new IllegalArgumentException("signal must not be null");
        }
        // Bridge strategy output to the legacy signal-based decision path.
        return decide(new TradingSignal(signal.action(), signal.reason()), market, lastPrice, maxOrderKrw);
    }

    /** Convenience overload that reads max order size from risk policy. */
    public Optional<OrderDecision> decide(
            TradingSignal signal,
            Market market,
            Money lastPrice,
            RiskPolicy riskPolicy
    ) {
        return decide(signal, market, lastPrice, riskPolicy.maxOrderKrw());
    }
}
