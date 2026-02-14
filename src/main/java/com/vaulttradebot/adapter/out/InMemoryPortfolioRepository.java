package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.port.out.PortfolioRepository;
import com.vaulttradebot.domain.portfolio.Position;
import com.vaulttradebot.domain.portfolio.snapshot.PositionSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryPortfolioRepository implements PortfolioRepository {
    private final ConcurrentHashMap<String, PositionSnapshot> positions = new ConcurrentHashMap<>();

    @Override
    public List<Position> findAll() {
        List<Position> result = new ArrayList<>(positions.size());
        for (PositionSnapshot snapshot : positions.values()) {
            result.add(Position.restore(snapshot));
        }
        return result;
    }

    @Override
    public Optional<Position> findByMarket(String marketSymbol) {
        PositionSnapshot snapshot = positions.get(marketSymbol);
        if (snapshot == null) {
            return Optional.empty();
        }
        return Optional.of(Position.restore(snapshot));
    }

    @Override
    public Position save(Position position, long expectedVersion) {
        String market = position.market().value();
        PositionSnapshot next = position.toSnapshot();
        positions.compute(market, (key, current) -> {
            long currentVersion = current == null ? -1L : current.version();
            if (currentVersion != expectedVersion) {
                throw new IllegalStateException(
                        "optimistic lock conflict: expected version " + expectedVersion + " but was " + currentVersion
                );
            }
            return next;
        });
        return Position.restore(next);
    }
}
