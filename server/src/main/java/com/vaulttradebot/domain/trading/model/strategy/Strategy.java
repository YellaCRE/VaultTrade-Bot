package com.vaulttradebot.domain.trading.model.strategy;

import com.vaulttradebot.domain.trading.model.strategy.vo.SignalDecision;
import com.vaulttradebot.domain.trading.model.strategy.vo.StrategyContext;

/** Produces a deterministic signal decision from normalized market context. */
public interface Strategy {
    /** Evaluates one strategy step and returns a standardized decision. */
    SignalDecision evaluate(StrategyContext context);
}
