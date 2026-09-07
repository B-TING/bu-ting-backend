-- Admin console schema (EVENT-ADMIN-API.md): multi-target auth, submission history,
-- TOP_LIKE/base reward payouts, ranking snapshot, report review fields, revision columns.

-- 1) Auth target: allow multiple selectable places per event, keep source coordinates,
--    add lifecycle status and optimistic locking.
ALTER TABLE zone_event_auth_target DROP CONSTRAINT uk_zone_event_auth_target_event;

ALTER TABLE zone_event_auth_target
    ADD COLUMN place_content_id VARCHAR(100),
    ADD COLUMN content_type_id VARCHAR(20),
    ADD COLUMN source_latitude DOUBLE PRECISION,
    ADD COLUMN source_longitude DOUBLE PRECISION,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;

-- Existing rows have no separate source snapshot; treat the current coordinates as the source.
UPDATE zone_event_auth_target
   SET source_latitude = latitude, source_longitude = longitude
 WHERE source_latitude IS NULL;

ALTER TABLE zone_event_auth_target
    ADD CONSTRAINT ck_zone_event_auth_target_status
        CHECK (status IN ('ACTIVE', 'REPLACED', 'CANCELLED'));

CREATE INDEX idx_zone_event_auth_target_event_status
    ON zone_event_auth_target (event_id, status);

-- 2) Submission history: one row per attempt, independent of the participation row.
CREATE TABLE zone_event_submission (
    submission_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participation_id UUID NOT NULL REFERENCES zone_event_participation(participation_id),
    attempt_no INTEGER NOT NULL,
    target_id UUID NOT NULL REFERENCES zone_event_auth_target(target_id),
    place_name VARCHAR(255) NOT NULL,
    target_latitude DOUBLE PRECISION NOT NULL,
    target_longitude DOUBLE PRECISION NOT NULL,
    radius_m INTEGER NOT NULL,
    guide_text_snapshot TEXT,
    media_file_key VARCHAR(512) NOT NULL,
    gps_lat DOUBLE PRECISION NOT NULL,
    gps_lng DOUBLE PRECISION NOT NULL,
    captured_at TIMESTAMPTZ,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    review_status VARCHAR(20) NOT NULL,
    rejection_reason VARCHAR(300),
    reviewed_by UUID,
    reviewed_at TIMESTAMPTZ,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_zone_event_submission_attempt UNIQUE (participation_id, attempt_no),
    CONSTRAINT ck_zone_event_submission_review_status
        CHECK (review_status IN ('UNDER_REVIEW', 'SUCCESS', 'REJECTED'))
);

CREATE INDEX idx_zone_event_submission_participation
    ON zone_event_submission (participation_id, attempt_no DESC);

ALTER TABLE zone_event_participation
    ADD COLUMN current_submission_id UUID REFERENCES zone_event_submission(submission_id);

-- 3) TOP_LIKE payout candidates (separate from the immediate reward_grant ledger).
CREATE TABLE reward_payout (
    payout_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES zone_event(event_id),
    participation_id UUID NOT NULL REFERENCES zone_event_participation(participation_id),
    rank_n INTEGER NOT NULL,
    like_count_at_close BIGINT NOT NULL,
    reward JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_ASSIGN',
    hold_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    scheduled_at TIMESTAMPTZ,
    confirmed_by UUID,
    confirmed_at TIMESTAMPTZ,
    mailed_at TIMESTAMPTZ,
    information_collected_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    failure_code VARCHAR(50),
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_reward_payout_participation UNIQUE (participation_id),
    CONSTRAINT ck_reward_payout_status CHECK (status IN (
        'PENDING_ASSIGN', 'PENDING_CONFIRM', 'CONFIRMED',
        'MAIL_SENT', 'INFO_COLLECTED', 'SENT', 'FAILED')),
    CONSTRAINT ck_reward_payout_hold CHECK (hold_status IN ('NONE', 'HELD_REPORT'))
);

CREATE INDEX idx_reward_payout_event ON reward_payout (event_id, rank_n);

-- 4) Base reward payout: separate confirmation step from photo approval.
CREATE TABLE base_reward_payout (
    payout_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participation_id UUID NOT NULL REFERENCES zone_event_participation(participation_id),
    reward JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_CONFIRM',
    hold_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    scheduled_at TIMESTAMPTZ,
    confirmed_by UUID,
    confirmed_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    failure_code VARCHAR(50),
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_base_reward_payout_participation UNIQUE (participation_id),
    CONSTRAINT ck_base_reward_payout_status
        CHECK (status IN ('PENDING_CONFIRM', 'CONFIRMED', 'PAID', 'FAILED')),
    CONSTRAINT ck_base_reward_payout_hold CHECK (hold_status IN ('NONE', 'HELD_REPORT'))
);

-- 5) Closing-time ranking snapshot (frozen at round/event close, ties kept explicit).
CREATE TABLE zone_event_ranking_snapshot (
    snapshot_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES zone_event(event_id),
    closed_at TIMESTAMPTZ NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    participation_id UUID NOT NULL REFERENCES zone_event_participation(participation_id),
    rank_n INTEGER NOT NULL,
    like_count_at_close BIGINT NOT NULL,
    tied BOOLEAN NOT NULL DEFAULT FALSE,
    finalized BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_zone_event_ranking_snapshot UNIQUE (event_id, version, participation_id)
);

CREATE INDEX idx_zone_event_ranking_snapshot_event
    ON zone_event_ranking_snapshot (event_id, version, rank_n);

-- 6) Report review fields: reviewer identity, decision note, revision.
--    RESOLVED was declared but never used in code; replace it with REVIEWING/UPHELD
--    so the status vocabulary matches the admin review flow (hold vs. payout status stay separate).
ALTER TABLE zone_event_report
    ADD COLUMN reviewed_by UUID,
    ADD COLUMN reviewed_at TIMESTAMPTZ,
    ADD COLUMN decision_note VARCHAR(500),
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;

ALTER TABLE zone_event_report DROP CONSTRAINT ck_zone_event_report_status;
ALTER TABLE zone_event_report
    ADD CONSTRAINT ck_zone_event_report_status
        CHECK (status IN ('OPEN', 'REVIEWING', 'UPHELD', 'DISMISSED'));

-- 7) Round: server-issued round number, display name, closed-at stamp, optimistic locking.
ALTER TABLE zone_event_round
    ADD COLUMN round_no INTEGER,
    ADD COLUMN name VARCHAR(255),
    ADD COLUMN closed_at TIMESTAMPTZ,
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uk_zone_event_round_no
    ON zone_event_round (round_no) WHERE round_no IS NOT NULL;

-- 8) Zone event: slot code (e.g. "1-A") and optimistic locking.
--    topN already lives inside excellence_reward (jsonb) via RewardSnapshot.topN().
ALTER TABLE zone_event
    ADD COLUMN slot_code VARCHAR(10),
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uk_zone_event_round_slot_code
    ON zone_event (round_id, slot_code) WHERE slot_code IS NOT NULL;

-- 9) Zone title definition: optimistic locking for admin edits.
ALTER TABLE zone_title_def
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
