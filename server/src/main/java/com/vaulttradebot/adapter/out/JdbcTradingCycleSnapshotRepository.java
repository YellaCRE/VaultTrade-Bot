package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.port.out.TradingCycleSnapshotRepository;
import com.vaulttradebot.application.usecase.TradingCycleSnapshot;
import com.vaulttradebot.domain.trading.vo.OrderDecisionType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "vault.persistence.mode", havingValue = "jdbc")
public class JdbcTradingCycleSnapshotRepository implements TradingCycleSnapshotRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcTradingCycleSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<TradingCycleSnapshot> findByCycleId(String cycleId) {
        return jdbcTemplate.query(
                        "SELECT * FROM trading_cycle_snapshot WHERE cycle_id=?",
                        this::mapSnapshot,
                        cycleId
                ).stream()
                .findFirst();
    }

    @Override
    public void save(TradingCycleSnapshot snapshot) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO trading_cycle_snapshot(
                        cycle_id, strategy_id, pair, timeframe, data_timestamp,
                        last_price, available_quote_krw, position_quantity,
                        signal_action, signal_reason,
                        risk_allowed, risk_reason_code,
                        decision_type, decision_reason, command_type, command_id,
                        outbox_event_id, latency_ms, error_reason, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    snapshot.cycleId(),
                    snapshot.strategyId(),
                    snapshot.pair(),
                    snapshot.timeframe(),
                    Timestamp.from(snapshot.dataTimestamp()),
                    snapshot.lastPrice(),
                    snapshot.availableQuoteKrw(),
                    snapshot.positionQuantity(),
                    snapshot.signalAction(),
                    snapshot.signalReason(),
                    snapshot.riskAllowed(),
                    snapshot.riskReasonCode(),
                    snapshot.decisionType().name(),
                    snapshot.decisionReason(),
                    snapshot.commandType(),
                    snapshot.commandId(),
                    snapshot.outboxEventId(),
                    snapshot.latencyMs(),
                    snapshot.errorReason(),
                    Timestamp.from(snapshot.createdAt())
            );
        } catch (DuplicateKeyException ignored) {
            // Ignore duplicate inserts so replay remains idempotent.
        }
    }

    private TradingCycleSnapshot mapSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new TradingCycleSnapshot(
                rs.getString("cycle_id"),
                rs.getString("strategy_id"),
                rs.getString("pair"),
                rs.getString("timeframe"),
                rs.getTimestamp("data_timestamp").toInstant(),
                rs.getBigDecimal("last_price"),
                rs.getBigDecimal("available_quote_krw"),
                rs.getBigDecimal("position_quantity"),
                rs.getString("signal_action"),
                rs.getString("signal_reason"),
                rs.getBoolean("risk_allowed"),
                rs.getString("risk_reason_code"),
                OrderDecisionType.valueOf(rs.getString("decision_type")),
                rs.getString("decision_reason"),
                rs.getString("command_type"),
                rs.getString("command_id"),
                rs.getString("outbox_event_id"),
                rs.getLong("latency_ms"),
                rs.getString("error_reason"),
                toInstant(rs.getTimestamp("created_at"))
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
