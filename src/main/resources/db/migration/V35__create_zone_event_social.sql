-- Zone event album social: likes, comments, reports (Phase 2).
-- Participation already carries like_count / comment_count / hidden / visibility (V30).

CREATE TABLE zone_event_like (
    like_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participation_id UUID NOT NULL REFERENCES zone_event_participation(participation_id),
    user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_zone_event_like_participation_user UNIQUE (participation_id, user_id)
);

CREATE INDEX idx_zone_event_like_participation ON zone_event_like (participation_id);

CREATE TABLE zone_event_comment (
    comment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participation_id UUID NOT NULL REFERENCES zone_event_participation(participation_id),
    user_id UUID NOT NULL REFERENCES users(id),
    content VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_zone_event_comment_participation
  ON zone_event_comment (participation_id, created_at);

CREATE TABLE zone_event_report (
    report_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participation_id UUID NOT NULL REFERENCES zone_event_participation(participation_id),
    reporter_id UUID NOT NULL REFERENCES users(id),
    reason_code VARCHAR(20) NOT NULL,
    memo VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_zone_event_report_participation_reporter UNIQUE (participation_id, reporter_id),
    CONSTRAINT ck_zone_event_report_reason CHECK (reason_code IN (
        'NOT_ON_SITE', 'INAPPROPRIATE', 'SPAM', 'OTHER')),
    CONSTRAINT ck_zone_event_report_status CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED'))
);

CREATE INDEX idx_zone_event_report_participation ON zone_event_report (participation_id);
