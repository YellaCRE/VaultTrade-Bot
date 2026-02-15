package com.vaulttradebot.application.port.out;

import com.vaulttradebot.application.idempotency.IdempotencyRecord;
import com.vaulttradebot.application.idempotency.IdempotencySnapshot;
import java.time.Instant;
import java.util.Optional;

public interface IdempotencyRepository {
    // Returns current key state if present.
    Optional<IdempotencyRecord> findByKey(String key);

    // Atomically reserves a key for first-time processing.
    boolean claim(String key, String requestHash, Instant createdAt, Instant expiresAt);

    // Stores final response snapshot for replay.
    void complete(String key, String requestHash, IdempotencySnapshot snapshot);

    // Removes unfinished claim for retry safety.
    void releaseClaim(String key, String requestHash);

    // Deletes expired records based on TTL.
    void removeExpired(Instant now);
}
