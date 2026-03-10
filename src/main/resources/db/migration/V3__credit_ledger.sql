-- Lock row: one row per client, holds current balance for SELECT FOR UPDATE
CREATE TABLE main.client_credit_balance (
    id          BIGINT PRIMARY KEY,
    client_id   BIGINT NOT NULL UNIQUE REFERENCES main.client_account(id),
    balance     BIGINT NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by           VARCHAR(50),
    created_date         TIMESTAMP,
    last_modified_by     VARCHAR(50),
    last_modified_date   TIMESTAMP,
    request_id           VARCHAR(100),
    session_id           TEXT
);
CREATE INDEX idx_client_credit_balance_client ON main.client_credit_balance(client_id);

-- Ledger: append-only, one entry per credit event
CREATE TABLE main.credit_ledger_entry (
    id            BIGINT PRIMARY KEY,
    client_id     BIGINT NOT NULL REFERENCES main.client_account(id),
    entry_type    VARCHAR(30) NOT NULL,
    amount        BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    reference     VARCHAR(200),
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by           VARCHAR(50),
    created_date         TIMESTAMP,
    last_modified_by     VARCHAR(50),
    last_modified_date   TIMESTAMP,
    request_id           VARCHAR(100),
    session_id           TEXT
);
CREATE INDEX idx_ledger_client_date ON main.credit_ledger_entry(client_id, created_date DESC);
