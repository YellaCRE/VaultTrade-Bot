package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.idempotency.IdempotencyRecord;
import com.vaulttradebot.application.idempotency.IdempotencySnapshot;
import com.vaulttradebot.application.port.out.IdempotencyRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryIdempotencyRepository implements IdempotencyRepository {
    private final ConcurrentHashMap<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

    @Override
    public Optional<IdempotencyRecord> findByKey(String key) {
        return Optional.ofNullable(records.get(key));
    }

    @Override
    public boolean claim(String key, String requestHash, Instant createdAt, Instant expiresAt) {
        // putIfAbsent gives atomic first-writer-wins behavior.
        IdempotencyRecord candidate = new IdempotencyRecord(key, requestHash, null, createdAt, expiresAt);
        return records.putIfAbsent(key, candidate) == null;
    }

    @Override
    public void complete(String key, String requestHash, IdempotencySnapshot snapshot) {
        // Complete only when hash matches the claim owner.
        records.computeIfPresent(key, (k, old) -> {
            if (!old.requestHash().equals(requestHash)) {
                return old;
            }
            return new IdempotencyRecord(k, old.requestHash(), snapshot, old.createdAt(), old.expiresAt());
        });
    }

    @Override
    public void releaseClaim(String key, String requestHash) {
        // Remove only incomplete claim owned by same request hash.
        records.computeIfPresent(key, (k, old) -> {
            if (!old.requestHash().equals(requestHash) || old.isCompleted()) {
                return old;
            }
            return null;
        });
    }

    @Override
    public void removeExpired(Instant now) {
        // Simple in-memory TTL eviction.
        records.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }
}
