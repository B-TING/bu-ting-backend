-- Operator console: round settlement report + audit trail (Phase 2).

CREATE TABLE zone_event_settlement_report (
    round_id UUID PRIMARY KEY REFERENCES zone_event_round(round_id),
    report JSONB NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE zone_event_audit_log (
    audit_id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id UUID,
    detail JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_zone_event_audit_log_target ON zone_event_audit_log (target_type, target_id);
