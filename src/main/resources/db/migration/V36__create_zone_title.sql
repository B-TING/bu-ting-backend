-- Zone titles: 6 zones x 3 tiers = 18 definitions (Phase 2).

CREATE TABLE zone_title_def (
    title_def_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title_code VARCHAR(50) NOT NULL,
    zone_id VARCHAR(30) NOT NULL,
    tier INTEGER NOT NULL,
    required_success_count INTEGER NOT NULL,
    title_name VARCHAR(100) NOT NULL,
    style VARCHAR(20) NOT NULL,
    color VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_zone_title_def_code UNIQUE (title_code),
    CONSTRAINT uk_zone_title_def_zone_tier UNIQUE (zone_id, tier),
    CONSTRAINT ck_zone_title_def_zone CHECK (zone_id IN (
        'HAEUNDAE_GIJANG', 'SUYEONG_NAMGU', 'CENTRAL_NORTH',
        'OLD_DOWNTOWN', 'YEONGDO', 'WESTERN_BUSAN')),
    CONSTRAINT ck_zone_title_def_tier CHECK (tier BETWEEN 1 AND 3)
);

CREATE TABLE user_zone_title (
    user_title_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    title_def_id UUID NOT NULL REFERENCES zone_title_def(title_def_id),
    zone_id VARCHAR(30) NOT NULL,
    equipped BOOLEAN NOT NULL DEFAULT FALSE,
    earned_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uk_user_zone_title UNIQUE (user_id, title_def_id)
);

-- At most one equipped title per user.
CREATE UNIQUE INDEX uk_user_zone_title_equipped
  ON user_zone_title (user_id) WHERE equipped;

CREATE INDEX idx_user_zone_title_user ON user_zone_title (user_id);

INSERT INTO zone_title_def
    (title_code, zone_id, tier, required_success_count, title_name, style, color)
VALUES
    ('HAEUNDAE_GIJANG_T1', 'HAEUNDAE_GIJANG', 1, 1, '해운대·기장 발자국', 'chip', '#A78BFA'),
    ('HAEUNDAE_GIJANG_T2', 'HAEUNDAE_GIJANG', 2, 3, '해운대·기장 러버', 'chip_text', '#8B5CF6'),
    ('HAEUNDAE_GIJANG_T3', 'HAEUNDAE_GIJANG', 3, 7, '해운대·기장 마스터', 'frame', '#6D28D9'),
    ('SUYEONG_NAMGU_T1', 'SUYEONG_NAMGU', 1, 1, '수영·남구 발자국', 'chip', '#A78BFA'),
    ('SUYEONG_NAMGU_T2', 'SUYEONG_NAMGU', 2, 3, '수영·남구 러버', 'chip_text', '#8B5CF6'),
    ('SUYEONG_NAMGU_T3', 'SUYEONG_NAMGU', 3, 7, '수영·남구 마스터', 'frame', '#6D28D9'),
    ('CENTRAL_NORTH_T1', 'CENTRAL_NORTH', 1, 1, '중부·북부 발자국', 'chip', '#A78BFA'),
    ('CENTRAL_NORTH_T2', 'CENTRAL_NORTH', 2, 3, '중부·북부 러버', 'chip_text', '#8B5CF6'),
    ('CENTRAL_NORTH_T3', 'CENTRAL_NORTH', 3, 7, '중부·북부 마스터', 'frame', '#6D28D9'),
    ('OLD_DOWNTOWN_T1', 'OLD_DOWNTOWN', 1, 1, '원도심 발자국', 'chip', '#A78BFA'),
    ('OLD_DOWNTOWN_T2', 'OLD_DOWNTOWN', 2, 3, '원도심 러버', 'chip_text', '#8B5CF6'),
    ('OLD_DOWNTOWN_T3', 'OLD_DOWNTOWN', 3, 7, '원도심 마스터', 'frame', '#6D28D9'),
    ('YEONGDO_T1', 'YEONGDO', 1, 1, '영도 발자국', 'chip', '#A78BFA'),
    ('YEONGDO_T2', 'YEONGDO', 2, 3, '영도 러버', 'chip_text', '#8B5CF6'),
    ('YEONGDO_T3', 'YEONGDO', 3, 7, '영도 마스터', 'frame', '#6D28D9'),
    ('WESTERN_BUSAN_T1', 'WESTERN_BUSAN', 1, 1, '서부산 발자국', 'chip', '#A78BFA'),
    ('WESTERN_BUSAN_T2', 'WESTERN_BUSAN', 2, 3, '서부산 러버', 'chip_text', '#8B5CF6'),
    ('WESTERN_BUSAN_T3', 'WESTERN_BUSAN', 3, 7, '서부산 마스터', 'frame', '#6D28D9')
ON CONFLICT (title_code) DO NOTHING;
