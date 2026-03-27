package com.vaulttradebot.adapter.out.upbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class UpbitRetryExecutorTest {
    @Test
    void retriesServerErrorsWithBackoffUntilSuccess() {
        // Verifies exponential backoff for retryable 5xx responses and eventual success.
        List<Long> sleeps = new ArrayList<>();
        UpbitRetryExecutor executor = new UpbitRetryExecutor(
                "upbit-quotation",
                3,
                200L,
                5_000L,
                1_000L,
                sleeps::add,
                () -> 1.0d
        );
        AtomicInteger attempts = new AtomicInteger();

        String result = executor.execute("getTicker", () -> {
            if (attempts.getAndIncrement() < 2) {
                throw HttpServerErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "busy",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8
                );
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(3);
        assertThat(sleeps).containsExactly(200L, 400L);
    }

    @Test
    void honorsRetryAfterForRateLimitResponses() {
        // Verifies that a 429 response uses Retry-After instead of the generic backoff policy.
        List<Long> sleeps = new ArrayList<>();
        UpbitRetryExecutor executor = new UpbitRetryExecutor(
                "upbit-quotation",
                2,
                200L,
                5_000L,
                1_000L,
                sleeps::add,
                () -> 1.0d
        );
        AtomicInteger attempts = new AtomicInteger();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, "3");

        String result = executor.execute("getTicker", () -> {
            if (attempts.getAndIncrement() == 0) {
                throw HttpClientErrorException.create(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "rate limited",
                        headers,
                        new byte[0],
                        StandardCharsets.UTF_8
                );
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(sleeps).containsExactly(3_000L);
    }

    @Test
    void doesNotRetryClientErrorsThatAreNotRateLimited() {
        // Verifies that request-shape problems fail immediately and do not sleep or retry.
        List<Long> sleeps = new ArrayList<>();
        UpbitRetryExecutor executor = new UpbitRetryExecutor(
                "upbit-trading",
                3,
                200L,
                5_000L,
                1_500L,
                sleeps::add,
                () -> 1.0d
        );

        assertThatThrownBy(() -> executor.execute("POST /v1/orders", () -> {
            throw HttpClientErrorException.create(
                    HttpStatus.BAD_REQUEST,
                    "bad request",
                    HttpHeaders.EMPTY,
                    new byte[0],
                    StandardCharsets.UTF_8
            );
        }))
                .isInstanceOf(HttpClientErrorException.class);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void retriesTransportFailuresUpToConfiguredLimit() {
        // Verifies that connection/time-out style failures are retried up to the configured limit.
        List<Long> sleeps = new ArrayList<>();
        UpbitRetryExecutor executor = new UpbitRetryExecutor(
                "upbit-quotation",
                1,
                250L,
                5_000L,
                1_000L,
                sleeps::add,
                () -> 1.0d
        );
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute("getMinuteCandles", () -> {
            attempts.incrementAndGet();
            throw new ResourceAccessException("timeout");
        }))
                .isInstanceOf(ResourceAccessException.class);
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(sleeps).containsExactly(250L);
    }
}
