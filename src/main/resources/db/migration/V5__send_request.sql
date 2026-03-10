CREATE TABLE main.send_request (
    id                  BIGINT PRIMARY KEY,
    client_id           BIGINT NOT NULL REFERENCES main.client_account(id),
    send_request_id     VARCHAR(200) NOT NULL,
    sender              VARCHAR(11) NOT NULL,
    message             TEXT NOT NULL,
    send_status         VARCHAR(30) NOT NULL DEFAULT 'ACCEPTED',
    schedule_time       TIMESTAMP,
    message_count       INT NOT NULL,
    segment_count       INT NOT NULL,
    reserved_credits    BIGINT NOT NULL,
    reservation_id      BIGINT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(50),
    created_date        TIMESTAMP,
    last_modified_by    VARCHAR(50),
    last_modified_date  TIMESTAMP,
    request_id          VARCHAR(100),
    session_id          TEXT,
    CONSTRAINT uq_send_request_client_ref UNIQUE (client_id, send_request_id)
);
CREATE INDEX idx_send_request_client_status ON main.send_request(client_id, send_status);
CREATE INDEX idx_send_request_scheduled ON main.send_request(schedule_time, send_status)
    WHERE schedule_time IS NOT NULL AND send_status = 'ACCEPTED';

CREATE TABLE main.send_request_recipient (
    id                  BIGINT PRIMARY KEY,
    send_request_id_fk  BIGINT NOT NULL REFERENCES main.send_request(id),
    client_id           BIGINT NOT NULL REFERENCES main.client_account(id),
    recipient           VARCHAR(20) NOT NULL,
    send_status         VARCHAR(30) NOT NULL DEFAULT 'ACCEPTED',
    gateway_message_id  VARCHAR(100),
    provider_message_id VARCHAR(200),
    segments_consumed   INT,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(50),
    created_date        TIMESTAMP,
    last_modified_by    VARCHAR(50),
    last_modified_date  TIMESTAMP,
    request_id          VARCHAR(100),
    session_id          TEXT
);
CREATE INDEX idx_send_request_recipient_request ON main.send_request_recipient(send_request_id_fk);
CREATE INDEX idx_send_request_recipient_client ON main.send_request_recipient(client_id, send_status);
