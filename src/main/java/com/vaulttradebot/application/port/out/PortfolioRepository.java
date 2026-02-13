package com.vaulttradebot.application.port.out;

import com.vaulttradebot.domain.shared.Position;
import java.util.List;
import java.util.Optional;

public interface PortfolioRepository {
    List<Position> findAll();

    Optional<Position> findByMarket(String marketSymbol);

    Position save(Position position);
}
