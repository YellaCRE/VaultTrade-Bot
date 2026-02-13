package com.vaulttradebot.domain.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class IdempotencyServiceTest {
    private final IdempotencyService service = new IdempotencyService();

    @Test
    void returnsSameKeyForSameInput() {
        Instant cycleTime = Instant.parse("2026-02-13T10:00:00Z");

        String key1 = service.generateKey("KRW-BTC", "BUY", "0.01", cycleTime, "reason");
        String key2 = service.generateKey("KRW-BTC", "BUY", "0.01", cycleTime, "reason");

        assertThat(key1).isEqualTo(key2);
        assertThat(key1).hasSize(64);
    }

    @Test
    void returnsDifferentKeyWhenInputChanges() {
        Instant cycleTime = Instant.parse("2026-02-13T10:00:00Z");

        String key1 = service.generateKey("KRW-BTC", "BUY", "0.01", cycleTime, "reason1");
        String key2 = service.generateKey("KRW-BTC", "BUY", "0.01", cycleTime, "reason2");

        assertThat(key1).isNotEqualTo(key2);
    }
}
