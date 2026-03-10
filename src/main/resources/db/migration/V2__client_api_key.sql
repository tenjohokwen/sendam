CREATE TABLE main.client_account (
    id                  BIGINT PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(50),
    created_date        TIMESTAMP,
    last_modified_by    VARCHAR(50),
    last_modified_date  TIMESTAMP,
    request_id          VARCHAR(100),
    session_id          TEXT
);

CREATE TABLE main.client_api_key (
    id                  BIGINT PRIMARY KEY,
    client_id           BIGINT NOT NULL REFERENCES main.client_account(id),
    key_prefix          VARCHAR(24) NOT NULL UNIQUE,
    key_hash            VARCHAR(64) NOT NULL,
    label               VARCHAR(100),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(50),
    created_date        TIMESTAMP,
    last_modified_by    VARCHAR(50),
    last_modified_date  TIMESTAMP,
    request_id          VARCHAR(100),
    session_id          TEXT
);

CREATE INDEX idx_client_api_key_prefix ON main.client_api_key(key_prefix);
