package com.vaulttradebot.adapter.out.upbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.config.VaultCircuitBreakerProperties;
import com.vaulttradebot.domain.resilience.CircuitBreakerOpenException;
import com.vaulttradebot.domain.resilience.InMemoryCircuitBreaker;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

class UpbitCircuitBreakerIntegrationTest {
    @Test
    void retryableInfrastructureFailuresOpenBreaker() {
        // Verifies that infrastructure failures are eligible to open the breaker.
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-03-15T00:00:00Z"));
        InMemoryCircuitBreaker circuitBreaker = new InMemoryCircuitBreaker(clock(now), breakerProperties(true, 1, 30_000L, 1));
        UpbitRetryExecutor retryExecutor = new UpbitRetryExecutor("upbit-quotation", 0, 1, 1, 1, millis -> { }, () -> 1.0d);

        assertThatThrownBy(() -> circuitBreaker.execute("upbit-quotation", () -> {
            throw new ResourceAccessException("timeout");
        })).isInstanceOf(ResourceAccessException.class);
        assertThatThrownBy(() -> circuitBreaker.execute("upbit-quotation", () -> "ok"))
                .isInstanceOf(CircuitBreakerOpenException.class);
        assertThat(retryExecutor.shouldTripCircuitBreaker(new ResourceAccessException("timeout"))).isTrue();
    }

    @Test
    void nonRetryableClientErrorsDoNotOpenBreaker() {
        // Verifies that bypassed request errors leave the breaker closed.
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-03-15T00:00:00Z"));
        InMemoryCircuitBreaker circuitBreaker = new InMemoryCircuitBreaker(clock(now), breakerProperties(true, 1, 30_000L, 1));

        assertThatThrownBy(() -> circuitBreaker.execute("upbit-trading", () -> {
            throw new com.vaulttradebot.domain.resilience.CircuitBreakerBypassException(
                    HttpClientErrorException.create(
                            HttpStatus.BAD_REQUEST,
                            "bad request",
                            HttpHeaders.EMPTY,
                            new byte[0],
                            StandardCharsets.UTF_8
                    )
            );
        })).isInstanceOf(com.vaulttradebot.domain.resilience.CircuitBreakerBypassException.class);

        assertThat(circuitBreaker.snapshot("upbit-trading").state()).isEqualTo(com.vaulttradebot.domain.resilience.CircuitBreakerState.CLOSED);
    }

    @Test
    void retryClassifierUsesRetryableStatusesForBreakerEligibility() {
        // Verifies the shared retry classifier that decides whether a failure should trip the breaker.
        UpbitRetryExecutor retryExecutor = new UpbitRetryExecutor("upbit-trading", 0, 1, 1, 1, millis -> { }, () -> 1.0d);

        assertThat(retryExecutor.shouldTripCircuitBreaker(HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "rate limited",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        ))).isTrue();
        assertThat(retryExecutor.shouldTripCircuitBreaker(HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "bad request",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        ))).isFalse();
    }

    private ClockPort clock(AtomicReference<Instant> now) {
        return now::get;
    }

    private VaultCircuitBreakerProperties breakerProperties(boolean enabled, int threshold, long openDurationMs, int halfOpenMaxCalls) {
        VaultCircuitBreakerProperties properties = new VaultCircuitBreakerProperties();
        properties.setEnabled(enabled);
        properties.setFailureThreshold(threshold);
        properties.setOpenDurationMs(openDurationMs);
        properties.setHalfOpenMaxCalls(halfOpenMaxCalls);
        return properties;
    }
}
