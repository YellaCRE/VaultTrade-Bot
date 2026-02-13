package com.vaulttradebot.domain.risk;

public record RiskEvaluation(boolean allowed, String reason) {
    public static RiskEvaluation allow(String reason) {
        return new RiskEvaluation(true, reason);
    }

    public static RiskEvaluation block(String reason) {
        return new RiskEvaluation(false, reason);
    }
}
