ALTER TABLE zone_title_def ADD CONSTRAINT uk_zone_title_def_zone_required_count UNIQUE (zone_id, required_success_count);
CREATE INDEX idx_zone_event_audit_log_actor ON zone_event_audit_log (actor_id, created_at);
CREATE INDEX idx_zone_event_audit_log_action ON zone_event_audit_log (action, created_at);
