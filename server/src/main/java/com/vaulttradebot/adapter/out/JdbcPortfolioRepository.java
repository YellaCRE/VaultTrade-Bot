package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.port.out.PortfolioRepository;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.portfolio.Position;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "vault.persistence.mode", havingValue = "jdbc")
public class JdbcPortfolioRepository implements PortfolioRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcPortfolioRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Position> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM portfolio_positions ORDER BY updated_at ASC",
                this::mapPosition
        );
    }

    @Override
    public Optional<Position> findByMarket(String marketSymbol) {
        return jdbcTemplate.query(
                        "SELECT * FROM portfolio_positions WHERE market_symbol=?",
                        this::mapPosition,
                        marketSymbol
                ).stream()
                .findFirst();
    }

    @Override
    public Position save(Position position, long expectedVersion) {
        if (expectedVersion == -1L) {
            insert(position);
            return findByMarket(position.market().value())
                    .orElseThrow(() -> new IllegalStateException("saved position not found"));
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE portfolio_positions
                SET quantity=?, avg_price_krw=?, realized_pnl_krw=?, version=?, updated_at=?
                WHERE market_symbol=? AND version=?
                """,
                position.quantity(),
                position.avgPrice().value(),
                position.realizedPnL(),
                position.version(),
                Timestamp.from(position.updatedAt()),
                position.market().value(),
                expectedVersion
        );
        if (updated == 0) {
            throw optimisticLockConflict(position.market().value(), expectedVersion);
        }
        return findByMarket(position.market().value())
                .orElseThrow(() -> new IllegalStateException("updated position not found"));
    }

    private void insert(Position position) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO portfolio_positions(
                        market_symbol, quantity, avg_price_krw, realized_pnl_krw, version, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    position.market().value(),
                    position.quantity(),
                    position.avgPrice().value(),
                    position.realizedPnL(),
                    position.version(),
                    Timestamp.from(position.updatedAt())
            );
        } catch (DuplicateKeyException ex) {
            throw optimisticLockConflict(position.market().value(), -1L);
        }
    }

    private Position mapPosition(ResultSet rs, int rowNum) throws SQLException {
        Market market = Market.of(rs.getString("market_symbol"));
        return Position.restore(new com.vaulttradebot.domain.portfolio.PositionSnapshot(
                market.value(),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("avg_price_krw"),
                rs.getBigDecimal("realized_pnl_krw"),
                rs.getLong("version"),
                toInstant(rs.getTimestamp("updated_at"))
        ));
    }

    private IllegalStateException optimisticLockConflict(String marketSymbol, long expectedVersion) {
        long currentVersion = jdbcTemplate.query(
                        "SELECT version FROM portfolio_positions WHERE market_symbol=?",
                        (rs, rowNum) -> rs.getLong("version"),
                        marketSymbol
                ).stream()
                .findFirst()
                .orElse(-1L);
        return new IllegalStateException(
                "optimistic lock conflict: expected version " + expectedVersion + " but was " + currentVersion
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
