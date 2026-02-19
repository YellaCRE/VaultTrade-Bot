-- PostgreSQL production schema
CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(64) PRIMARY KEY,
    market VARCHAR(32) NOT NULL,
    order_type VARCHAR(16) NOT NULL,
    side VARCHAR(16) NOT NULL,
    quantity NUMERIC(30,8) NOT NULL,
    price_krw NUMERIC(30,0) NOT NULL,
    minimum_profit_price_krw NUMERIC(30,0),
    strategy_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    executed_quantity NUMERIC(30,8) NOT NULL,
    executed_amount_krw NUMERIC(30,0) NOT NULL,
    version BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS outbox (
    id VARCHAR(64) PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    payload_version INT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    next_attempt_at TIMESTAMPTZ,
    dead_lettered_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS trading_cycle_snapshot (
    cycle_id VARCHAR(96) PRIMARY KEY,
    strategy_id VARCHAR(128) NOT NULL,
    pair VARCHAR(32) NOT NULL,
    timeframe VARCHAR(16) NOT NULL,
    data_timestamp TIMESTAMPTZ NOT NULL,
    last_price NUMERIC(30,8),
    available_quote_krw NUMERIC(30,8),
    position_quantity NUMERIC(30,8),
    signal_action VARCHAR(32) NOT NULL,
    signal_reason TEXT NOT NULL,
    risk_allowed BOOLEAN NOT NULL,
    risk_reason_code VARCHAR(64),
    decision_type VARCHAR(16) NOT NULL,
    decision_reason TEXT NOT NULL,
    command_type VARCHAR(16),
    command_id VARCHAR(128),
    outbox_event_id VARCHAR(96),
    latency_ms BIGINT NOT NULL,
    error_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_orders_market_created_at ON orders (market, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_outbox_ready
    ON outbox (next_attempt_at, created_at)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_outbox_dead_lettered_at ON outbox (dead_lettered_at) WHERE dead_lettered_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_cycle_pair_ts ON trading_cycle_snapshot (pair, data_timestamp DESC);
