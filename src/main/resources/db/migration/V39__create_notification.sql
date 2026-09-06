-- Push notification: device tokens, zone subscriptions, per-type preferences, send log (Phase 2).

CREATE TABLE user_device_token (
    token_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    fcm_token VARCHAR(512) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_device_token UNIQUE (fcm_token),
    CONSTRAINT ck_user_device_token_platform CHECK (platform IN ('IOS', 'ANDROID'))
);

CREATE INDEX idx_user_device_token_user ON user_device_token (user_id);

CREATE TABLE user_zone_subscription (
    subscription_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    zone_id VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_zone_subscription UNIQUE (user_id, zone_id),
    CONSTRAINT ck_user_zone_subscription_zone CHECK (zone_id IN (
        'HAEUNDAE_GIJANG', 'SUYEONG_NAMGU', 'CENTRAL_NORTH',
        'OLD_DOWNTOWN', 'YEONGDO', 'WESTERN_BUSAN'))
);

CREATE TABLE user_notification_preference (
    preference_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    notification_type VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_user_notification_preference UNIQUE (user_id, notification_type)
);

CREATE TABLE push_notification_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kind VARCHAR(30) NOT NULL,
    target VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    recipient_count INTEGER NOT NULL DEFAULT 0,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    result VARCHAR(20) NOT NULL
);
