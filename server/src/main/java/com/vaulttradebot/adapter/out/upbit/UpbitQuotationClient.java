package com.vaulttradebot.adapter.out.upbit;

import com.vaulttradebot.adapter.out.upbit.dto.UpbitMinuteCandleResponse;
import com.vaulttradebot.adapter.out.upbit.dto.UpbitTickerResponse;
import com.vaulttradebot.config.VaultMarketDataProperties;
import com.vaulttradebot.config.VaultCircuitBreakerProperties;
import com.vaulttradebot.domain.resilience.CircuitBreaker;
import com.vaulttradebot.domain.resilience.CircuitBreakerBypassException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class UpbitQuotationClient {
    private static final DateTimeFormatter UPBIT_TO_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final String BREAKER_NAME = "upbit-quotation";

    private final RestClient restClient;
    private final UpbitRetryExecutor retryExecutor;
    private final CircuitBreaker circuitBreaker;
    private final VaultCircuitBreakerProperties circuitBreakerProperties;

    public UpbitQuotationClient(
            RestClient.Builder restClientBuilder,
            VaultMarketDataProperties properties,
            CircuitBreaker circuitBreaker,
            VaultCircuitBreakerProperties circuitBreakerProperties
    ) {
        VaultMarketDataProperties.Retry retry = properties.getUpbit().getRetry();
        this.restClient = restClientBuilder
                .baseUrl(properties.getUpbit().getBaseUrl())
                .build();
        this.retryExecutor = new UpbitRetryExecutor(
                "upbit-quotation",
                retry.getMaxAttempts(),
                retry.getBaseDelayMs(),
                retry.getMaxDelayMs(),
                retry.getRateLimitDelayMs()
        );
        this.circuitBreaker = circuitBreaker;
        this.circuitBreakerProperties = circuitBreakerProperties;
    }

    public UpbitTickerResponse getTicker(String market) {
        try {
            UpbitTickerResponse[] body = executeProtected("getTicker", () -> restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/ticker")
                            .queryParam("markets", market)
                            .build())
                    .retrieve()
                    .body(UpbitTickerResponse[].class));
            if (body == null || body.length != 1 || body[0] == null) {
                throw new IllegalStateException("upbit ticker response was empty for market " + market);
            }
            return body[0];
        } catch (RestClientException ex) {
            throw new IllegalStateException("failed to fetch upbit ticker for market " + market, ex);
        }
    }

    public List<UpbitMinuteCandleResponse> getMinuteCandles(String market, int unit, int count, Instant to) {
        try {
            UpbitMinuteCandleResponse[] body = executeProtected("getMinuteCandles", () -> restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/candles/minutes/{unit}")
                            .queryParam("market", market)
                            .queryParam("count", count)
                            .queryParam("to", UPBIT_TO_FORMAT.format(to.atOffset(ZoneOffset.UTC)))
                            .build(unit))
                    .retrieve()
                    .body(UpbitMinuteCandleResponse[].class));
            if (body == null) {
                return List.of();
            }
            return List.of(body);
        } catch (RestClientException ex) {
            throw new IllegalStateException(
                    "failed to fetch upbit minute candles for market %s and unit %d".formatted(market, unit),
                    ex
            );
        }
    }

    private <T> T executeProtected(String operationName, java.util.function.Supplier<T> action) {
        if (!circuitBreakerProperties.isEnabled()) {
            return retryExecutor.execute(operationName, action);
        }

        try {
            // Let the breaker observe only the final infrastructure failure after retry exhaustion.
            return circuitBreaker.execute(BREAKER_NAME, () -> {
                try {
                    return retryExecutor.execute(operationName, action);
                } catch (RuntimeException error) {
                    if (retryExecutor.shouldTripCircuitBreaker(error)) {
                        throw error;
                    }
                    // Business/request errors should be returned to callers without opening the breaker.
                    throw new CircuitBreakerBypassException(error);
                }
            });
        } catch (CircuitBreakerBypassException bypass) {
            throw bypass.getCause();
        }
    }
}
