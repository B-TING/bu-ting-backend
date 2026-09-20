-- 토큰 종류를 구분한다. 지금까지는 액세스 토큰 하나만 발급해 1시간 뒤 만료되면
-- 재로그인 외에 방법이 없었다. 리프레시 토큰을 같은 표에 담되 용도를 구분해,
-- 인증 필터가 리프레시로 API를 호출하는 것을 막을 수 있게 한다.
--
-- 기존 행은 전부 액세스 토큰이다. 리프레시가 발급된 적이 없다.

ALTER TABLE opaque_tokens
    ADD COLUMN token_type VARCHAR(20) NOT NULL DEFAULT 'ACCESS';

ALTER TABLE opaque_tokens
    ADD CONSTRAINT ck_opaque_tokens_type CHECK (token_type IN ('ACCESS', 'REFRESH'));

-- 사용자별 종류별 조회가 발급·폐기 경로의 주 사용처다.
CREATE INDEX idx_opaque_tokens_user_type ON opaque_tokens (user_id, token_type);
