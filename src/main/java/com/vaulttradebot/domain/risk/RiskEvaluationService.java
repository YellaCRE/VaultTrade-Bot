package com.vaulttradebot.domain.risk;

import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RiskEvaluationService {
    public RiskEvaluation evaluate(RiskContext context, RiskPolicy policy) {
        if (context.requestedOrderKrw().compareTo(policy.maxOrderKrw()) > 0) {
            return RiskEvaluation.block("order amount exceeds maxOrderKrw");
        }
        if (context.currentExposureRatio().compareTo(policy.maxExposureRatio()) > 0) {
            return RiskEvaluation.block("exposure ratio exceeds maxExposureRatio");
        }
        if (context.dailyLossRatio().compareTo(policy.maxDailyLossRatio()) > 0) {
            return RiskEvaluation.block("daily loss ratio exceeds maxDailyLossRatio");
        }
        if (context.lastOrderAt() != null) {
            Duration elapsed = Duration.between(context.lastOrderAt(), context.now());
            if (elapsed.compareTo(policy.cooldown()) < 0) {
                return RiskEvaluation.block("cooldown is active");
            }
        }
        return RiskEvaluation.allow("risk checks passed");
    }
}
