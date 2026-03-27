package com.vaulttradebot.application.port.out;

import com.vaulttradebot.domain.ops.KillSwitchState;
import java.util.Optional;

public interface KillSwitchStateRepository {
    Optional<KillSwitchState> load();

    KillSwitchState save(KillSwitchState state);

    void clear();
}
