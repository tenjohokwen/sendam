CREATE INDEX idx_srr_client_date
    ON main.send_request_recipient(client_id, created_date);
