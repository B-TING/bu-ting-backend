-- Zone event core: event types, events, auth targets, participations.
-- Zones are stored as ChatZone enum names with a CHECK constraint (no zone table).

CREATE TABLE zone_event_type (
    type_code VARCHAR(30) PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    requires_upload BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE zone_event (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    zone_id VARCHAR(30) NOT NULL,
    type_code VARCHAR(30) NOT NULL REFERENCES zone_event_type(type_code),
    round_id UUID,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    starts_at TIMESTAMPTZ NOT NULL,
    duration_minutes INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    base_reward JSONB NOT NULL,
    excellence_reward JSONB,
    success_limit_per_user INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT ck_zone_event_zone_id CHECK (zone_id IN (
        'HAEUNDAE_GIJANG', 'SUYEONG_NAMGU', 'CENTRAL_NORTH',
        'OLD_DOWNTOWN', 'YEONGDO', 'WESTERN_BUSAN')),
    CONSTRAINT ck_zone_event_status CHECK (status IN (
        'SCHEDULED', 'ACTIVE', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_zone_event_duration CHECK (duration_minutes > 0),
    CONSTRAINT ck_zone_event_success_limit CHECK (success_limit_per_user >= 1)
);

CREATE INDEX idx_zone_event_zone_status_starts
  ON zone_event (zone_id, status, starts_at);

CREATE TABLE zone_event_auth_target (
    target_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES zone_event(event_id),
    target_kind VARCHAR(20) NOT NULL,
    landmark_id VARCHAR(100),
    place_name VARCHAR(255) NOT NULL,
    guide_text TEXT,
    example_file_key VARCHAR(512),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    radius_m INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT uk_zone_event_auth_target_event UNIQUE (event_id),
    CONSTRAINT ck_zone_event_auth_target_kind CHECK (target_kind IN ('PLACE', 'OBJECT')),
    CONSTRAINT ck_zone_event_auth_target_radius CHECK (radius_m BETWEEN 30 AND 500)
);

CREATE TABLE zone_event_participation (
    participation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES zone_event(event_id),
    user_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL,
    success BOOLEAN,
    media_file_key VARCHAR(512),
    content VARCHAR(300),
    gps_lat DOUBLE PRECISION NOT NULL,
    gps_lng DOUBLE PRECISION NOT NULL,
    submit_gps_lat DOUBLE PRECISION,
    submit_gps_lng DOUBLE PRECISION,
    captured_at TIMESTAMPTZ,
    visibility VARCHAR(10) NOT NULL DEFAULT 'PUBLIC',
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    like_count BIGINT NOT NULL DEFAULT 0,
    comment_count INTEGER NOT NULL DEFAULT 0,
    cancel_reason VARCHAR(30),
    fail_reason VARCHAR(30),
    joined_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    reviewed_by UUID,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_zone_event_participation_status CHECK (status IN (
        'JOINED', 'SUBMITTED', 'UNDER_REVIEW', 'SUCCESS', 'FAIL', 'CANCELLED', 'REVOKED')),
    CONSTRAINT ck_zone_event_participation_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE'))
);

-- At most one open (incomplete) participation per user per event.
CREATE UNIQUE INDEX uk_zone_event_participation_open
  ON zone_event_participation (event_id, user_id)
  WHERE status IN ('JOINED', 'SUBMITTED', 'UNDER_REVIEW');

CREATE INDEX idx_zone_event_participation_user
  ON zone_event_participation (user_id, joined_at DESC);

CREATE INDEX idx_zone_event_participation_ranking
  ON zone_event_participation (event_id, status, like_count DESC, completed_at);
