ALTER TABLE main.send_request
    ADD COLUMN finalized_at TIMESTAMP;

CREATE INDEX idx_send_request_finalized_at ON main.send_request(finalized_at)
    WHERE finalized_at IS NOT NULL AND send_status IN ('FINALIZED', 'FAIL_FINALIZED');
