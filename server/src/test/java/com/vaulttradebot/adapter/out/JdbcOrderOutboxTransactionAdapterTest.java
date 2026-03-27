package com.vaulttradebot.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class JdbcOrderOutboxTransactionAdapterTest {
    private JdbcTemplate jdbcTemplate;
    private JdbcOrderOutboxTransactionAdapter adapter;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:tx-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS tx_probe(id VARCHAR(32) PRIMARY KEY, probe_value VARCHAR(32))");
        jdbcTemplate.update("DELETE FROM tx_probe");

        TransactionTemplate transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        adapter = new JdbcOrderOutboxTransactionAdapter(transactionTemplate);
    }

    @Test
    void commitsWhenActionSucceeds() {
        // Verifies JDBC transaction commits writes when action completes successfully.
        adapter.execute(() -> jdbcTemplate.update("INSERT INTO tx_probe(id, probe_value) VALUES (?, ?)", "ok-1", "value"));

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tx_probe WHERE id='ok-1'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void rollsBackWhenActionThrows() {
        // Verifies JDBC transaction rolls back all writes when action throws an exception.
        assertThatThrownBy(() -> adapter.execute(() -> {
            jdbcTemplate.update("INSERT INTO tx_probe(id, probe_value) VALUES (?, ?)", "fail-1", "value");
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boom");

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tx_probe WHERE id='fail-1'", Integer.class);
        assertThat(count).isEqualTo(0);
    }
}
