package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.outbox.OutboxMessage;
import com.vaulttradebot.application.port.out.OutboxRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "vault.persistence.mode", havingValue = "jdbc")
public class JdbcOutboxRepository implements OutboxRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(OutboxMessage message) {
        jdbcTemplate.update(
                """
                INSERT INTO outbox(
                    id, aggregate_type, aggregate_id, event_type, payload, payload_version,
                    occurred_at, created_at, published_at, attempt_count, last_error, next_attempt_at, dead_lettered_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                message.id(),
                message.aggregateType(),
                message.aggregateId(),
                message.eventType(),
                message.payload(),
                message.payloadVersion(),
                Timestamp.from(message.occurredAt()),
                Timestamp.from(message.createdAt()),
                message.publishedAt() == null ? null : Timestamp.from(message.publishedAt()),
                message.attemptCount(),
                message.lastError(),
                message.nextAttemptAt() == null ? null : Timestamp.from(message.nextAttemptAt()),
                message.deadLetteredAt() == null ? null : Timestamp.from(message.deadLetteredAt())
        );
    }

    @Override
    public List<OutboxMessage> findReadyToPublish(int limit, Instant now) {
        return jdbcTemplate.query(
                """
                SELECT * FROM outbox
                WHERE published_at IS NULL
                  AND dead_lettered_at IS NULL
                  AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                ORDER BY created_at ASC
                LIMIT ?
                """,
                this::mapMessage,
                Timestamp.from(now),
                Math.max(0, limit)
        );
    }

    @Override
    public void markPublished(String messageId, Instant publishedAt) {
        jdbcTemplate.update(
                "UPDATE outbox SET published_at=? WHERE id=? AND published_at IS NULL",
                Timestamp.from(publishedAt),
                messageId
        );
    }

    @Override
    public void markFailed(String messageId, int attemptCount, Instant nextAttemptAt, String lastError) {
        jdbcTemplate.update(
                "UPDATE outbox SET attempt_count=?, next_attempt_at=?, last_error=? WHERE id=? AND published_at IS NULL",
                attemptCount,
                Timestamp.from(nextAttemptAt),
                lastError,
                messageId
        );
    }

    @Override
    public void markDeadLettered(String messageId, Instant deadLetteredAt, String lastError) {
        jdbcTemplate.update(
                """
                UPDATE outbox
                SET dead_lettered_at=?, next_attempt_at=NULL, last_error=?
                WHERE id=? AND published_at IS NULL
                """,
                Timestamp.from(deadLetteredAt),
                lastError,
                messageId
        );
    }

    @Override
    public List<OutboxMessage> findDeadLettered(int limit) {
        return jdbcTemplate.query(
                """
                SELECT * FROM outbox
                WHERE dead_lettered_at IS NOT NULL
                ORDER BY dead_lettered_at ASC
                LIMIT ?
                """,
                this::mapMessage,
                Math.max(0, limit)
        );
    }

    @Override
    public void redriveDeadLetter(String messageId, Instant nextAttemptAt) {
        jdbcTemplate.update(
                """
                UPDATE outbox
                SET dead_lettered_at=NULL, published_at=NULL, attempt_count=0, last_error=NULL, next_attempt_at=?
                WHERE id=?
                """,
                Timestamp.from(nextAttemptAt),
                messageId
        );
    }

    @Override
    public List<OutboxMessage> findAll() {
        return jdbcTemplate.query("SELECT * FROM outbox ORDER BY created_at ASC", this::mapMessage);
    }

    private OutboxMessage mapMessage(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxMessage(
                rs.getString("id"),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getString("event_type"),
                rs.getString("payload"),
                rs.getInt("payload_version"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                toInstant(rs.getTimestamp("published_at")),
                rs.getInt("attempt_count"),
                rs.getString("last_error"),
                toInstant(rs.getTimestamp("next_attempt_at")),
                toInstant(rs.getTimestamp("dead_lettered_at"))
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
