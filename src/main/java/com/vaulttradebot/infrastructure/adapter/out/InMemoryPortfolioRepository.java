package com.vaulttradebot.infrastructure.adapter.out;

import com.vaulttradebot.application.port.out.PortfolioRepository;
import com.vaulttradebot.domain.shared.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryPortfolioRepository implements PortfolioRepository {
    private final ConcurrentHashMap<String, Position> positions = new ConcurrentHashMap<>();

    @Override
    public List<Position> findAll() {
        return new ArrayList<>(positions.values());
    }

    @Override
    public Optional<Position> findByMarket(String marketSymbol) {
        return Optional.ofNullable(positions.get(marketSymbol));
    }

    @Override
    public Position save(Position position) {
        positions.put(position.market().symbol(), position);
        return position;
    }
}
