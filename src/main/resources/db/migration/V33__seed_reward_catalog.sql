-- Phase 1 reward catalog seed. Points are granted as a fixed catalog entry
-- (the actual amount comes from each event's base_reward snapshot); the badge
-- is an example spot badge.

INSERT INTO reward_catalog (reward_type, code, name, point_amount, active) VALUES
    ('POINT', 'POINT_BASE', '기본 포인트', 50, TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO reward_catalog (reward_type, code, name, active) VALUES
    ('BADGE', 'SPOT_GWANGAN_BRIDGE', '광안대교 스팟', TRUE)
ON CONFLICT (code) DO NOTHING;
