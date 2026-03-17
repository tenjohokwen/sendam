-- Singleton balance row for the platform credit account
CREATE TABLE main.platform_credit_balance (
    id                   BIGINT PRIMARY KEY,
    balance              BIGINT NOT NULL DEFAULT 0,
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by           VARCHAR(50),
    created_date         TIMESTAMP,
    last_modified_by     VARCHAR(50),
    last_modified_date   TIMESTAMP,
    request_id           VARCHAR(100),
    session_id           TEXT
);

-- Ledger: append-only, one entry per platform credit event
CREATE TABLE main.platform_credit_ledger_entry (
    id                   BIGINT PRIMARY KEY,
    entry_type           VARCHAR(30) NOT NULL,
    amount               BIGINT NOT NULL,
    balance_after        BIGINT NOT NULL,
    reference            VARCHAR(200),
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by           VARCHAR(50),
    created_date         TIMESTAMP,
    last_modified_by     VARCHAR(50),
    last_modified_date   TIMESTAMP,
    request_id           VARCHAR(100),
    session_id           TEXT
);

CREATE INDEX idx_platform_ledger_type_date ON main.platform_credit_ledger_entry(entry_type, created_date DESC);

-- Seed the singleton balance row; id=1 is safe because TSID-generated IDs encode
-- a high-bit timestamp value far above 1. The application never inserts a new row;
-- it always updates this singleton via SELECT FOR UPDATE.
INSERT INTO main.platform_credit_balance (id, balance, status, created_date)
VALUES (1, 0, 'ACTIVE', NOW());
