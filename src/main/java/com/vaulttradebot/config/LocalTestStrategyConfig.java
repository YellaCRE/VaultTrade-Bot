package com.vaulttradebot.config;

import com.vaulttradebot.domain.trading.model.strategy.Strategy;
import com.vaulttradebot.domain.trading.model.strategy.vo.SignalDecision;
import com.vaulttradebot.domain.trading.model.strategy.vo.StrategyContext;
import com.vaulttradebot.domain.trading.vo.SignalAction;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local-test")
public class LocalTestStrategyConfig {

    private static final class LocalTestSingleShotBuyStrategy implements Strategy {
        private final AtomicBoolean emitted = new AtomicBoolean(false);

        @Override
        public SignalDecision evaluate(StrategyContext context) {
            if (emitted.compareAndSet(false, true)) {
                return new SignalDecision(
                        SignalAction.BUY,
                        1.0d,
                        "LOCAL_TEST_FORCE_BUY_ONCE",
                        context.now(),
                        context.symbol(),
                        context.timeframe()
                );
            }
            return SignalDecision.hold(
                    "LOCAL_TEST_ALREADY_EMITTED",
                    context.now(),
                    context.symbol(),
                    context.timeframe()
            );
        }
    }

    @Bean
    @Primary
    public Strategy localTestStrategy() {
        return new LocalTestSingleShotBuyStrategy();
    }
}
