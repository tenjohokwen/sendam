CREATE INDEX idx_topup_client_date
    ON main.topup_request(client_id, created_date DESC);
