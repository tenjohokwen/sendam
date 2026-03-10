CREATE TABLE main.topup_request (
    id              BIGINT PRIMARY KEY,
    client_id       BIGINT NOT NULL REFERENCES main.client_account(id),
    amount          BIGINT NOT NULL CHECK (amount > 0),
    transaction_id  VARCHAR(200) NOT NULL,
    payment_type    VARCHAR(50) NOT NULL,
    account_number  VARCHAR(50),
    topup_status    VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL',
    approved_at     TIMESTAMP,
    rejected_at     TIMESTAMP,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by           VARCHAR(50),
    created_date         TIMESTAMP,
    last_modified_by     VARCHAR(50),
    last_modified_date   TIMESTAMP,
    request_id           VARCHAR(100),
    session_id           TEXT,
    CONSTRAINT uq_topup_client_txn UNIQUE (client_id, transaction_id)
);
CREATE INDEX idx_topup_client_status ON main.topup_request(client_id, topup_status);
