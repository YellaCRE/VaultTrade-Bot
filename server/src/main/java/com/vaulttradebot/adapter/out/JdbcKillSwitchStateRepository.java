package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.port.out.KillSwitchStateRepository;
import com.vaulttradebot.domain.ops.KillSwitchState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "vault.persistence.mode", havingValue = "jdbc")
public class JdbcKillSwitchStateRepository implements KillSwitchStateRepository {
    private static final String STATE_KEY = "kill-switch";

    private final JdbcTemplate jdbcTemplate;

    public JdbcKillSwitchStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<KillSwitchState> load() {
        return jdbcTemplate.query(
                        "SELECT activated_at, reason FROM bot_runtime_state WHERE state_key=?",
                        this::mapState,
                        STATE_KEY
                ).stream()
                .findFirst();
    }

    @Override
    public KillSwitchState save(KillSwitchState state) {
        int updated = jdbcTemplate.update(
                """
                UPDATE bot_runtime_state
                SET activated_at=?, reason=?
                WHERE state_key=?
                """,
                Timestamp.from(state.activatedAt()),
                state.reason(),
                STATE_KEY
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO bot_runtime_state(state_key, activated_at, reason)
                    VALUES (?, ?, ?)
                    """,
                    STATE_KEY,
                    Timestamp.from(state.activatedAt()),
                    state.reason()
            );
        }
        return state;
    }

    @Override
    public void clear() {
        jdbcTemplate.update("DELETE FROM bot_runtime_state WHERE state_key=?", STATE_KEY);
    }

    private KillSwitchState mapState(ResultSet rs, int rowNum) throws SQLException {
        return new KillSwitchState(
                rs.getTimestamp("activated_at").toInstant(),
                rs.getString("reason")
        );
    }
}
