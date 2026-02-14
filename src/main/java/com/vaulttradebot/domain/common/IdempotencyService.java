package com.vaulttradebot.domain.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyService {
    public String generateKey(
            String marketSymbol,
            String side,
            String quantity,
            Instant cycleTimestamp,
            String reason
    ) {
        String raw = "%s|%s|%s|%s|%s".formatted(marketSymbol, side, quantity, cycleTimestamp.toString(), reason);
        return sha256(raw);
    }

    public IdempotencyKey generate(
            String marketSymbol,
            String side,
            String quantity,
            Instant cycleTimestamp,
            String reason
    ) {
        return new IdempotencyKey(generateKey(marketSymbol, side, quantity, cycleTimestamp, reason));
    }

    public boolean isValid(String key) {
        return key != null && key.matches("^[a-f0-9]{64}$");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
