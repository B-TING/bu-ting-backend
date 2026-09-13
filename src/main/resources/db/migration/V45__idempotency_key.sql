-- Generic per-key replay store for POST endpoints that accept an Idempotency-Key header
-- (currently: zone-event review approve/reject, issue #241).
CREATE TABLE idempotency_key (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    endpoint VARCHAR(100) NOT NULL,
    fingerprint VARCHAR(300) NOT NULL,
    response_body TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
