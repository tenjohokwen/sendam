-- Phase 20: Account Freeze Infrastructure
-- Adds freeze lifecycle columns to client_account and creates the platform_freeze_state singleton table.

-- Section 1: Add freeze columns to client_account
ALTER TABLE main.client_account
    ADD COLUMN frozen              BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN frozen_at           TIMESTAMP,
    ADD COLUMN freeze_reason       TEXT,
    ADD COLUMN freeze_resolved_at  TIMESTAMP,
    ADD COLUMN freeze_resolution   TEXT;

-- Section 2: Platform-level freeze state singleton table
-- Mirrors the structure of platform_credit_balance (V11); one row, always present.
-- shortfall_amount is nullable — a manually-initiated admin freeze has no shortfall;
-- only an automated low-balance trigger (Phase 22 PFLAT-03) sets this field.
CREATE TABLE main.platform_freeze_state (
    id                  BIGINT       PRIMARY KEY,
    frozen              BOOLEAN      NOT NULL DEFAULT FALSE,
    frozen_at           TIMESTAMP,
    freeze_reason       TEXT,
    shortfall_amount    BIGINT,
    freeze_resolved_at  TIMESTAMP,
    freeze_resolution   TEXT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(50),
    created_date        TIMESTAMP,
    last_modified_by    VARCHAR(50),
    last_modified_date  TIMESTAMP,
    request_id          VARCHAR(100),
    session_id          TEXT
);

-- Section 3: Seed the singleton row.
-- id=1 is safe because TSID-generated IDs encode a high-bit timestamp value far above 1.
-- The application never inserts a new row; it always updates this singleton via SELECT FOR UPDATE.
INSERT INTO main.platform_freeze_state (id, frozen, status, created_date)
VALUES (1, FALSE, 'ACTIVE', NOW());
