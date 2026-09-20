-- 회전된 리프레시 토큰을 지우는 대신 revoked_at으로 남기면서 행이 계속 쌓인다.
-- 정리 스케줄러가 "만료됐거나 폐기된 지 오래된" 행을 매일 지우는데, 이 조건이
-- 전체 스캔이 되지 않도록 두 축에 인덱스를 둔다. PostgreSQL은 OR 조건을
-- BitmapOr로 두 인덱스를 함께 쓴다.

CREATE INDEX idx_opaque_tokens_expires_at ON opaque_tokens (expires_at);

-- 폐기된 행은 전체의 일부라 부분 인덱스로 충분하다.
CREATE INDEX idx_opaque_tokens_revoked_at ON opaque_tokens (revoked_at)
    WHERE revoked_at IS NOT NULL;
