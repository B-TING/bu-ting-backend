-- City grade rise history (Phase 2). The grade itself is computed on read from
-- zone titles; only the moment it rises is recorded here.

CREATE TABLE user_city_grade_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    grade VARCHAR(20) NOT NULL,
    reached_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_user_city_grade CHECK (grade IN (
        'BEGINNER', 'EXPLORER', 'MASTER', 'TRUE_BUSAN'))
);

CREATE INDEX idx_user_city_grade_history_user
  ON user_city_grade_history (user_id, reached_at DESC);
