CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(64) PRIMARY KEY,
    market VARCHAR(32) NOT NULL,
    order_type VARCHAR(16) NOT NULL,
    side VARCHAR(16) NOT NULL,
    quantity DECIMAL(30,8) NOT NULL,
    price_krw DECIMAL(30,0) NOT NULL,
    minimum_profit_price_krw DECIMAL(30,0),
    strategy_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    status VARCHAR(32) NOT NULL,
    executed_quantity DECIMAL(30,8) NOT NULL,
    executed_amount_krw DECIMAL(30,0) NOT NULL,
    version BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS outbox (
    id VARCHAR(64) PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload CLOB NOT NULL,
    payload_version INT NOT NULL DEFAULT 1,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error CLOB,
    next_attempt_at TIMESTAMP,
    dead_lettered_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS trading_cycle_snapshot (
    cycle_id VARCHAR(96) PRIMARY KEY,
    strategy_id VARCHAR(128) NOT NULL,
    pair VARCHAR(32) NOT NULL,
    timeframe VARCHAR(16) NOT NULL,
    data_timestamp TIMESTAMP NOT NULL,
    last_price DECIMAL(30,8),
    available_quote_krw DECIMAL(30,8),
    position_quantity DECIMAL(30,8),
    signal_action VARCHAR(32) NOT NULL,
    signal_reason CLOB NOT NULL,
    risk_allowed BOOLEAN NOT NULL,
    risk_reason_code VARCHAR(64),
    decision_type VARCHAR(16) NOT NULL,
    decision_reason CLOB NOT NULL,
    command_type VARCHAR(16),
    command_id VARCHAR(128),
    outbox_event_id VARCHAR(96),
    latency_ms BIGINT NOT NULL,
    error_reason CLOB,
    created_at TIMESTAMP NOT NULL
);

ALTER TABLE outbox ADD COLUMN IF NOT EXISTS payload_version INT NOT NULL DEFAULT 1;
ALTER TABLE outbox ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE outbox ADD COLUMN IF NOT EXISTS last_error CLOB;
ALTER TABLE outbox ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP;
ALTER TABLE outbox ADD COLUMN IF NOT EXISTS dead_lettered_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_orders_market_created_at ON orders(market, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_publish_scan ON outbox(published_at, dead_lettered_at, next_attempt_at, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_created_at ON outbox(created_at);
CREATE INDEX IF NOT EXISTS idx_cycle_pair_ts ON trading_cycle_snapshot(pair, data_timestamp);
