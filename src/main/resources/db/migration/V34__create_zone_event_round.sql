-- Zone event rounds, slots, and rain-fallback targets (Phase 2).

CREATE TABLE zone_event_round (
    round_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    round_type VARCHAR(20) NOT NULL DEFAULT 'REGULAR',
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    timezone VARCHAR(40) NOT NULL DEFAULT 'Asia/Seoul',
    status VARCHAR(20) NOT NULL,
    settled_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT ck_zone_event_round_type CHECK (round_type IN ('REGULAR', 'GUERRILLA')),
    CONSTRAINT ck_zone_event_round_status CHECK (status IN (
        'SCHEDULED', 'OPEN', 'CLOSED', 'SETTLED'))
);

CREATE INDEX idx_zone_event_round_status_starts
  ON zone_event_round (status, starts_at);

CREATE TABLE zone_event_round_slot (
    slot_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    round_id UUID NOT NULL REFERENCES zone_event_round(round_id),
    slot_kind VARCHAR(20) NOT NULL,
    zone_id VARCHAR(30) NOT NULL,
    event_id UUID REFERENCES zone_event(event_id),
    pair_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_zone_event_round_slot UNIQUE (round_id, zone_id),
    CONSTRAINT ck_zone_event_round_slot_kind CHECK (slot_kind IN ('AUTH', 'MUKJJIPPA')),
    CONSTRAINT ck_zone_event_round_slot_zone CHECK (zone_id IN (
        'HAEUNDAE_GIJANG', 'SUYEONG_NAMGU', 'CENTRAL_NORTH',
        'OLD_DOWNTOWN', 'YEONGDO', 'WESTERN_BUSAN'))
);

CREATE INDEX idx_zone_event_round_slot_round ON zone_event_round_slot (round_id);

CREATE TABLE zone_event_backup_target (
    target_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    round_id UUID NOT NULL REFERENCES zone_event_round(round_id),
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
    CONSTRAINT ck_zone_event_backup_target_kind CHECK (target_kind IN ('PLACE', 'OBJECT')),
    CONSTRAINT ck_zone_event_backup_target_radius CHECK (radius_m BETWEEN 30 AND 500)
);

CREATE INDEX idx_zone_event_backup_target_round ON zone_event_backup_target (round_id);

-- Link existing events to rounds now that the round table exists.
ALTER TABLE zone_event
  ADD CONSTRAINT fk_zone_event_round
  FOREIGN KEY (round_id) REFERENCES zone_event_round(round_id);
