package com.vaulttradebot.adapter.out.upbit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaulttradebot.adapter.out.upbit.dto.UpbitCancelOrderRequest;
import com.vaulttradebot.adapter.out.upbit.dto.UpbitCreateOrderRequest;
import com.vaulttradebot.adapter.out.upbit.dto.UpbitOrderResponse;
import com.vaulttradebot.config.VaultCircuitBreakerProperties;
import com.vaulttradebot.config.VaultTradingProperties;
import com.vaulttradebot.domain.resilience.CircuitBreaker;
import com.vaulttradebot.domain.resilience.CircuitBreakerBypassException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "vault.trading.provider", havingValue = "upbit")
public class UpbitTradingClient {
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String HMAC_ALGORITHM = "HmacSHA512";
    private static final String HASH_ALGORITHM = "SHA-512";
    private static final String BREAKER_NAME = "upbit-trading";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String accessKey;
    private final String secretKey;
    private final UpbitRetryExecutor retryExecutor;
    private final CircuitBreaker circuitBreaker;
    private final VaultCircuitBreakerProperties circuitBreakerProperties;

    public UpbitTradingClient(
            RestClient.Builder restClientBuilder,
            VaultTradingProperties properties,
            ObjectMapper objectMapper,
            CircuitBreaker circuitBreaker,
            VaultCircuitBreakerProperties circuitBreakerProperties
    ) {
        VaultTradingProperties.Upbit upbit = properties.getUpbit();
        this.restClient = restClientBuilder
                .baseUrl(upbit.getBaseUrl())
                .build();
        this.objectMapper = objectMapper;
        this.accessKey = requireText(upbit.getAccessKey(), "vault.trading.upbit.access-key");
        this.secretKey = requireText(upbit.getSecretKey(), "vault.trading.upbit.secret-key");
        VaultTradingProperties.Retry retry = upbit.getRetry();
        this.retryExecutor = new UpbitRetryExecutor(
                "upbit-trading",
                retry.getMaxAttempts(),
                retry.getBaseDelayMs(),
                retry.getMaxDelayMs(),
                retry.getRateLimitDelayMs()
        );
        this.circuitBreaker = circuitBreaker;
        this.circuitBreakerProperties = circuitBreakerProperties;
    }

    public UpbitOrderResponse placeLimitOrder(UpbitCreateOrderRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("market", request.market());
        body.put("side", request.side());
        body.put("volume", request.volume());
        body.put("price", request.price());
        body.put("ord_type", request.ordType());
        if (StringUtils.hasText(request.identifier())) {
            body.put("identifier", request.identifier());
        }
        // Trading endpoints require an authenticated body hash, so we keep the payload shape deterministic.
        return post("/v1/orders", body);
    }

    public UpbitOrderResponse cancelOrder(String uuid) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uuid", requireText(uuid, "order uuid"));
        return delete("/v1/order", body);
    }

    public UpbitOrderResponse cancelOrder(UpbitCancelOrderRequest request) {
        return cancelOrder(request.uuid());
    }

    private UpbitOrderResponse post(String path, Map<String, Object> body) {
        try {
            return executeProtected("POST " + path, () -> restClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + createJwt(body))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(UpbitOrderResponse.class));
        } catch (RestClientException ex) {
            throw new IllegalStateException("failed to call upbit trading endpoint " + path, ex);
        }
    }

    private UpbitOrderResponse delete(String path, Map<String, Object> body) {
        try {
            return executeProtected("DELETE " + path, () -> restClient.method(org.springframework.http.HttpMethod.DELETE)
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + createJwt(body))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(UpbitOrderResponse.class));
        } catch (RestClientException ex) {
            throw new IllegalStateException("failed to call upbit trading endpoint " + path, ex);
        }
    }

    private <T> T executeProtected(String operationName, java.util.function.Supplier<T> action) {
        if (!circuitBreakerProperties.isEnabled()) {
            return retryExecutor.execute(operationName, action);
        }

        try {
            // Trading calls share the same ordering: breaker outside, retries inside, HTTP at the edge.
            return circuitBreaker.execute(BREAKER_NAME, () -> {
                try {
                    return retryExecutor.execute(operationName, action);
                } catch (RuntimeException error) {
                    if (retryExecutor.shouldTripCircuitBreaker(error)) {
                        throw error;
                    }
                    // Invalid orders or auth mistakes should not poison the breaker state.
                    throw new CircuitBreakerBypassException(error);
                }
            });
        } catch (CircuitBreakerBypassException bypass) {
            throw bypass.getCause();
        }
    }

    private String createJwt(Map<String, Object> params) {
        try {
            String queryString = toQueryString(params);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("access_key", accessKey);
            payload.put("nonce", UUID.randomUUID().toString());
            if (!queryString.isBlank()) {
                // Upbit signs the SHA-512 hash of the request payload instead of the raw JSON body.
                payload.put("query_hash", sha512(queryString));
                payload.put("query_hash_alg", "SHA512");
            }

            String headerJson = objectMapper.writeValueAsString(Map.of("alg", "HS512", "typ", "JWT"));
            String payloadJson = objectMapper.writeValueAsString(payload);
            String encodedHeader = base64Url(headerJson.getBytes(StandardCharsets.UTF_8));
            String encodedPayload = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signatureInput = encodedHeader + "." + encodedPayload;
            String signature = base64Url(hmacSha512(signatureInput));
            return signatureInput + "." + signature;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize upbit jwt payload", ex);
        }
    }

    private String toQueryString(Map<String, Object> params) {
        try {
            LinkedHashMap<String, Object> converted = objectMapper.convertValue(
                    params,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    }
            );
            // Preserve insertion order so the generated hash stays stable across runs.
            return converted.entrySet().stream()
                    .filter(entry -> entry.getValue() != null)
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .reduce((left, right) -> left + "&" + right)
                    .orElse("");
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("failed to convert upbit params into query string", ex);
        }
    }

    private String sha512(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return toHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("sha-512 algorithm is unavailable", ex);
        }
    }

    private byte[] hmacSha512(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to sign upbit jwt", ex);
        }
    }

    private String base64Url(byte[] bytes) {
        return BASE64_URL_ENCODER.encodeToString(bytes);
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private String requireText(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + " must be configured when vault.trading.provider=upbit");
        }
        return value;
    }
}
