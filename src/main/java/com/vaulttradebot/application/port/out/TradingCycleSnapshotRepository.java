package com.vaulttradebot.application.port.out;

import com.vaulttradebot.application.usecase.TradingCycleSnapshot;
import java.util.Optional;

public interface TradingCycleSnapshotRepository {
    Optional<TradingCycleSnapshot> findByCycleId(String cycleId);

    void save(TradingCycleSnapshot snapshot);
}
