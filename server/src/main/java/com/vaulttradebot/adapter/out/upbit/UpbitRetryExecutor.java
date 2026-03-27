package com.vaulttradebot.adapter.out.upbit;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

final class UpbitRetryExecutor {
    private static final Logger log = LoggerFactory.getLogger(UpbitRetryExecutor.class);

    private final String clientName;
    private final int maxAttempts;
    private final long baseDelayMs;
    private final long maxDelayMs;
    private final long rateLimitDelayMs;
    private final LongConsumer sleeper;
    private final DoubleSupplier jitterSource;

    UpbitRetryExecutor(
            String clientName,
            int maxAttempts,
            long baseDelayMs,
            long maxDelayMs,
            long rateLimitDelayMs
    ) {
        this(
                clientName,
                maxAttempts,
                baseDelayMs,
                maxDelayMs,
                rateLimitDelayMs,
                millis -> {
                    try {
                        Thread.sleep(millis);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("upbit retry sleep was interrupted", ex);
                    }
                },
                () -> ThreadLocalRandom.current().nextDouble()
        );
    }

    UpbitRetryExecutor(
            String clientName,
            int maxAttempts,
            long baseDelayMs,
            long maxDelayMs,
            long rateLimitDelayMs,
            LongConsumer sleeper,
            DoubleSupplier jitterSource
    ) {
        this.clientName = clientName;
        this.maxAttempts = Math.max(0, maxAttempts);
        this.baseDelayMs = Math.max(1L, baseDelayMs);
        this.maxDelayMs = Math.max(this.baseDelayMs, maxDelayMs);
        this.rateLimitDelayMs = Math.max(1L, rateLimitDelayMs);
        this.sleeper = sleeper;
        this.jitterSource = jitterSource;
    }

    <T> T execute(String operationName, Supplier<T> action) {
        int attempt = 0;
        while (true) {
            try {
                return action.get();
            } catch (RestClientResponseException ex) {
                if (!isRetryableStatus(ex.getStatusCode().value()) || attempt >= maxAttempts) {
                    throw ex;
                }
                long delayMs = resolveDelay(ex, attempt);
                log.warn(
                        "Retrying {} {} after HTTP {} on attempt {} in {} ms",
                        clientName,
                        operationName,
                        ex.getStatusCode().value(),
                        attempt + 1,
                        delayMs
                );
                sleeper.accept(delayMs);
                attempt++;
            } catch (ResourceAccessException ex) {
                if (attempt >= maxAttempts) {
                    throw ex;
                }
                long delayMs = computeBackoffDelay(attempt);
                log.warn(
                        "Retrying {} {} after transport failure on attempt {} in {} ms",
                        clientName,
                        operationName,
                        attempt + 1,
                        delayMs
                );
                sleeper.accept(delayMs);
                attempt++;
            }
        }
    }

    // Only infrastructure-style failures should contribute to circuit-breaker state changes.
    boolean shouldTripCircuitBreaker(RuntimeException error) {
        if (error instanceof ResourceAccessException) {
            return true;
        }
        if (error instanceof RestClientResponseException responseException) {
            return isRetryableStatus(responseException.getStatusCode().value());
        }
        return false;
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private long resolveDelay(RestClientResponseException ex, int attempt) {
        if (ex.getStatusCode().value() == 429) {
            // Respect server-provided cooldowns when Upbit explicitly rate-limits the client.
            return parseRetryAfter(ex.getResponseHeaders()).orElse(rateLimitDelayMs);
        }
        return computeBackoffDelay(attempt);
    }

    private java.util.OptionalLong parseRetryAfter(HttpHeaders headers) {
        if (headers == null) {
            return java.util.OptionalLong.empty();
        }

        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null || retryAfter.isBlank()) {
            return java.util.OptionalLong.empty();
        }

        try {
            return java.util.OptionalLong.of(Math.max(0L, Long.parseLong(retryAfter.trim()) * 1000L));
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(retryAfter).toInstant();
                long millis = Duration.between(Instant.now(), retryAt).toMillis();
                return java.util.OptionalLong.of(Math.max(0L, millis));
            } catch (Exception parseError) {
                return java.util.OptionalLong.empty();
            }
        }
    }

    private long computeBackoffDelay(int attempt) {
        long delay = baseDelayMs;
        for (int index = 0; index < attempt; index++) {
            if (delay >= maxDelayMs) {
                return maxDelayMs;
            }
            delay = Math.min(maxDelayMs, delay * 2);
        }
        double jitterMultiplier = 0.5d + (Math.max(0d, Math.min(1d, jitterSource.getAsDouble())) * 0.5d);
        return Math.min(maxDelayMs, Math.max(1L, Math.round(delay * jitterMultiplier)));
    }
}
