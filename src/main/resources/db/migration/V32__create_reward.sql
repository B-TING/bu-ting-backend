-- Reward catalog, grants, point ledger/balance, and badges.
-- Grants reference zone_event / participation by id (FK), created in V30.

CREATE TABLE reward_catalog (
    reward_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reward_type VARCHAR(20) NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    point_amount INTEGER,
    image_file_key VARCHAR(512),
    stock INTEGER,
    monthly_cap INTEGER,
    valid_days INTEGER,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT uk_reward_catalog_code UNIQUE (code),
    CONSTRAINT ck_reward_catalog_type CHECK (reward_type IN ('POINT', 'BADGE', 'COUPON', 'GIFTICON'))
);

CREATE TABLE reward_grant (
    grant_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    reward_id UUID NOT NULL REFERENCES reward_catalog(reward_id),
    participation_id UUID REFERENCES zone_event_participation(participation_id),
    event_id UUID REFERENCES zone_event(event_id),
    round_id UUID,
    grant_reason VARCHAR(30) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uk_reward_grant_participation_reason_reward
      UNIQUE (participation_id, grant_reason, reward_id)
);

CREATE INDEX idx_reward_grant_user ON reward_grant (user_id, granted_at DESC);

CREATE TABLE user_point_ledger (
    ledger_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    amount INTEGER NOT NULL,
    reason VARCHAR(30) NOT NULL,
    grant_id UUID REFERENCES reward_grant(grant_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_point_ledger_user ON user_point_ledger (user_id, created_at);

CREATE TABLE user_point_balance (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    balance INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_user_point_balance_non_negative CHECK (balance >= 0)
);

CREATE TABLE user_badge (
    user_badge_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    reward_id UUID NOT NULL REFERENCES reward_catalog(reward_id),
    grant_id UUID NOT NULL REFERENCES reward_grant(grant_id),
    earned_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_user_badge_user_reward UNIQUE (user_id, reward_id)
);
