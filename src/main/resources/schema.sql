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

ALTER TABLE outbox ADD COLUMN IF NOT EXISTS payload_version INT NOT NULL DEFAULT 1;
ALTER TABLE outbox ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE outbox ADD COLUMN IF NOT EXISTS last_error CLOB;
ALTER TABLE outbox ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP;
ALTER TABLE outbox ADD COLUMN IF NOT EXISTS dead_lettered_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_orders_market_created_at ON orders(market, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_publish_scan ON outbox(published_at, dead_lettered_at, next_attempt_at, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_created_at ON outbox(created_at);
