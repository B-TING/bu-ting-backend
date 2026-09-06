-- User coupon inventory for physical rewards issued at settlement (Phase 2).

CREATE TABLE user_coupon (
    user_coupon_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    reward_id UUID NOT NULL REFERENCES reward_catalog(reward_id),
    grant_id UUID NOT NULL REFERENCES reward_grant(grant_id),
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ,
    used_at TIMESTAMPTZ,
    CONSTRAINT ck_user_coupon_status CHECK (status IN ('ISSUED', 'USED', 'EXPIRED'))
);

CREATE INDEX idx_user_coupon_user ON user_coupon (user_id, status);
