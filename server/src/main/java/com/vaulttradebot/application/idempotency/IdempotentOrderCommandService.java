package com.vaulttradebot.application.idempotency;

import com.vaulttradebot.application.port.out.IdempotencyRepository;
import com.vaulttradebot.application.usecase.CycleResult;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class IdempotentOrderCommandService {
    private final IdempotencyRepository idempotencyRepository;

    public IdempotentOrderCommandService(IdempotencyRepository idempotencyRepository) {
        this.idempotencyRepository = idempotencyRepository;
    }

    public Optional<CycleResult> claimOrReplay(
            String key,
            String requestHash,
            Instant now,
            Duration ttl
    ) {
        // Best-effort cleanup before reading current key state.
        idempotencyRepository.removeExpired(now);
        Optional<IdempotencyRecord> existing = idempotencyRepository.findByKey(key);
        if (existing.isPresent()) {
            return resolveExisting(existing.get(), key, requestHash);
        }

        // First caller claims the key and continues normal execution.
        boolean claimed = idempotencyRepository.claim(key, requestHash, now, now.plus(ttl));
        if (claimed) {
            return Optional.empty();
        }

        // If claim lost due to race, read the winning record and resolve replay/conflict.
        IdempotencyRecord raced = idempotencyRepository.findByKey(key)
                .orElseThrow(() -> new IllegalStateException("idempotency claim race without record"));
        return resolveExisting(raced, key, requestHash);
    }

    public void complete(String key, String requestHash, CycleResult result) {
        // Persist response snapshot for safe replay.
        idempotencyRepository.complete(key, requestHash, IdempotencySnapshot.from(result));
    }

    public void releaseClaim(String key, String requestHash) {
        // Clear unfinished claim so transient failures can be retried.
        idempotencyRepository.releaseClaim(key, requestHash);
    }

    private Optional<CycleResult> resolveExisting(IdempotencyRecord record, String key, String requestHash) {
        if (!record.requestHash().equals(requestHash)) {
            throw new IdempotencyConflictException("same idempotency key was used with different payload: " + key);
        }
        if (record.isCompleted()) {
            return Optional.of(record.snapshot().toCycleResult());
        }
        // Another request already owns this key and is still processing it.
        throw new IllegalStateException("idempotent command is still in progress: " + key);
    }
}
