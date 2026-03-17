-- Phase 21: Enhanced Credit Reservation
-- Adds buffered-vs-raw reservation data and per-recipient expected segment storage
-- so Phase 22 booking can detect segment count deviations.

-- raw_expected_credits: unbuffered expected credit cost (expectedSegments * recipientCount)
-- stored alongside reservedCredits (which holds the +1-per-recipient buffered amount)
ALTER TABLE main.send_request
    ADD COLUMN raw_expected_credits BIGINT NOT NULL DEFAULT 0;

-- expected_segments: per-recipient expected segment count at reservation time
-- parallel to segments_consumed (written at booking); Phase 22 compares them per-recipient
ALTER TABLE main.send_request_recipient
    ADD COLUMN expected_segments INT NOT NULL DEFAULT 0;
