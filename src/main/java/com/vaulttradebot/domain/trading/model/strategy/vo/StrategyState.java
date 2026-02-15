package com.vaulttradebot.domain.trading.model.strategy.vo;

import com.vaulttradebot.domain.trading.vo.SignalAction;
import java.time.Instant;

/** Internal per-key memory for debounce/cooldown control. */
public record StrategyState(
        SignalAction lastSignal,
        Instant cooldownUntil
) {
}
