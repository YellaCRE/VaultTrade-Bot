package com.vaulttradebot.domain.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RiskEvaluationServiceTest {
    private final RiskEvaluationService service = new RiskEvaluationService();

    @Test
    void blocksWhenOrderAmountExceedsLimit() {
        Instant now = Instant.parse("2026-02-13T10:00:00Z");
        RiskContext context = new RiskContext(
                new BigDecimal("200000"),
                new BigDecimal("0.10"),
                new BigDecimal("0.01"),
                now,
                null
        );
        RiskPolicy policy = new RiskPolicy(
                new BigDecimal("100000"),
                new BigDecimal("0.30"),
                new BigDecimal("0.03"),
                Duration.ofSeconds(10)
        );

        RiskEvaluation result = service.evaluate(context, policy);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("maxOrderKrw");
    }

    @Test
    void blocksWhenCooldownIsActive() {
        Instant now = Instant.parse("2026-02-13T10:00:20Z");
        RiskContext context = new RiskContext(
                new BigDecimal("100000"),
                new BigDecimal("0.10"),
                new BigDecimal("0.01"),
                now,
                Instant.parse("2026-02-13T10:00:15Z")
        );
        RiskPolicy policy = new RiskPolicy(
                new BigDecimal("100000"),
                new BigDecimal("0.30"),
                new BigDecimal("0.03"),
                Duration.ofSeconds(10)
        );

        RiskEvaluation result = service.evaluate(context, policy);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("cooldown");
    }
}
