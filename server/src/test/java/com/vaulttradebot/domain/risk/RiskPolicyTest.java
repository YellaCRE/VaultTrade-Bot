package com.vaulttradebot.domain.risk;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class RiskPolicyTest {

    @Test
    void acceptsValidPolicy() {
        // Verifies policy creation succeeds for valid threshold and duration values.
        assertThatCode(() -> new RiskPolicy(
                new BigDecimal("5000"),
                new BigDecimal("100000"),
                new BigDecimal("0.30"),
                new BigDecimal("0.03"),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                ZoneId.of("Asia/Seoul"),
                new BigDecimal("0.0005"),
                new BigDecimal("0.0020")
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidBoundaries() {
        // Verifies policy validation rejects invalid min/max and negative duration boundaries.
        assertThatThrownBy(() -> new RiskPolicy(
                new BigDecimal("100001"),
                new BigDecimal("100000"),
                new BigDecimal("0.30"),
                new BigDecimal("0.03"),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                ZoneId.of("Asia/Seoul"),
                new BigDecimal("0.0005"),
                new BigDecimal("0.0020")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxOrderKrw must be >= minOrderKrw");

        assertThatThrownBy(() -> new RiskPolicy(
                new BigDecimal("5000"),
                new BigDecimal("100000"),
                new BigDecimal("0.30"),
                new BigDecimal("0.03"),
                Duration.ofSeconds(-1),
                Duration.ofSeconds(5),
                ZoneId.of("Asia/Seoul"),
                new BigDecimal("0.0005"),
                new BigDecimal("0.0020")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be negative");
    }
}
