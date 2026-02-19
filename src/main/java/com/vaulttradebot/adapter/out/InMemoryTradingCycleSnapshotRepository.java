package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.port.out.TradingCycleSnapshotRepository;
import com.vaulttradebot.application.usecase.TradingCycleSnapshot;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "vault.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryTradingCycleSnapshotRepository implements TradingCycleSnapshotRepository {
    private final ConcurrentHashMap<String, TradingCycleSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public Optional<TradingCycleSnapshot> findByCycleId(String cycleId) {
        return Optional.ofNullable(snapshots.get(cycleId));
    }

    @Override
    public void save(TradingCycleSnapshot snapshot) {
        // putIfAbsent keeps the first persisted cycle snapshot for deterministic replay.
        snapshots.putIfAbsent(snapshot.cycleId(), snapshot);
    }

    ConcurrentHashMap<String, TradingCycleSnapshot> snapshot() {
        return new ConcurrentHashMap<>(snapshots);
    }

    void restore(ConcurrentHashMap<String, TradingCycleSnapshot> state) {
        snapshots.clear();
        snapshots.putAll(state);
    }
}
