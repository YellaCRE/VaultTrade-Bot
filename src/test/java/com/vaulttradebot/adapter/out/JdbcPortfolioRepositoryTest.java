package com.vaulttradebot.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaulttradebot.domain.common.vo.Asset;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Price;
import com.vaulttradebot.domain.common.vo.Quantity;
import com.vaulttradebot.domain.portfolio.Position;
import com.vaulttradebot.domain.portfolio.event.BalanceAdjusted;
import java.math.BigDecimal;
import java.time.Instant;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcPortfolioRepositoryTest {
    private static final Market MARKET = Market.of("KRW-BTC");
    private static final Instant NOW = Instant.parse("2026-03-27T12:00:00Z");

    private JdbcTemplate jdbcTemplate;
    private JdbcPortfolioRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:portfolio-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS portfolio_positions (
                    market_symbol VARCHAR(32) PRIMARY KEY,
                    quantity DECIMAL(30,8) NOT NULL,
                    avg_price_krw DECIMAL(30,8) NOT NULL,
                    realized_pnl_krw DECIMAL(30,0) NOT NULL,
                    version BIGINT NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """
        );
        jdbcTemplate.update("DELETE FROM portfolio_positions");

        repository = new JdbcPortfolioRepository(jdbcTemplate);
    }

    @Test
    void insertsNewPositionWhenExpectedVersionIsMinusOne() {
        // Verifies a brand-new position is inserted when no prior row exists.
        Position position = Position.open(MARKET, NOW);

        Position saved = repository.save(position, -1L);

        assertThat(saved.market().value()).isEqualTo("KRW-BTC");
        assertThat(saved.version()).isEqualTo(0L);
        assertThat(repository.findByMarket("KRW-BTC")).isPresent();
    }

    @Test
    void updatesExistingPositionWhenVersionMatches() {
        // Verifies optimistic-lock update succeeds when the expected version matches.
        Position opened = Position.open(MARKET, NOW);
        repository.save(opened, -1L);

        Position updated = Position.open(MARKET, NOW);
        updated.apply(new BalanceAdjusted(
                MARKET,
                Quantity.of(new BigDecimal("0.12500000")),
                Price.of(new BigDecimal("140000000.00000000"), Asset.krw()),
                NOW.plusSeconds(30)
        ));

        Position saved = repository.save(updated, 0L);

        assertThat(saved.quantity()).isEqualByComparingTo("0.12500000");
        assertThat(saved.avgPrice().value()).isEqualByComparingTo("140000000.00000000");
        assertThat(saved.version()).isEqualTo(1L);
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void throwsWhenExpectedVersionDoesNotMatchCurrentVersion() {
        // Verifies stale writes are rejected when the stored version has already changed.
        repository.save(Position.open(MARKET, NOW), -1L);

        Position updated = Position.open(MARKET, NOW);
        updated.apply(new BalanceAdjusted(
                MARKET,
                Quantity.of(new BigDecimal("0.01000000")),
                Price.of(new BigDecimal("130000000.00000000"), Asset.krw()),
                NOW.plusSeconds(10)
        ));

        assertThatThrownBy(() -> repository.save(updated, -1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("optimistic lock conflict: expected version -1 but was 0");
    }
}
