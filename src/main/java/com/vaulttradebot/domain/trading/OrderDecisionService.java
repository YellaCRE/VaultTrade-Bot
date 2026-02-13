package com.vaulttradebot.domain.trading;

import com.vaulttradebot.domain.shared.market.Market;
import com.vaulttradebot.domain.shared.market.Money;
import com.vaulttradebot.domain.shared.order.Side;
import com.vaulttradebot.domain.risk.RiskPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class OrderDecisionService {
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

    public Optional<OrderDecision> decide(
            TradingSignal signal,
            Market market,
            Money lastPrice,
            RiskPolicy riskPolicy
    ) {
        return decide(signal, market, lastPrice, riskPolicy.maxOrderKrw());
    }
}
