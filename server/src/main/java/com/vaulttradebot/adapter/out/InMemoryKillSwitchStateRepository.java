package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.port.out.KillSwitchStateRepository;
import com.vaulttradebot.domain.ops.KillSwitchState;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "vault.persistence.mode", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryKillSwitchStateRepository implements KillSwitchStateRepository {
    private final AtomicReference<KillSwitchState> state = new AtomicReference<>();

    @Override
    public Optional<KillSwitchState> load() {
        return Optional.ofNullable(state.get());
    }

    @Override
    public KillSwitchState save(KillSwitchState state) {
        this.state.set(state);
        return state;
    }

    @Override
    public void clear() {
        state.set(null);
    }
}
