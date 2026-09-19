-- 일정 장소가 사용자가 고른 것인지 서버가 추천으로 채운 것인지 구분한다.
-- 구분이 없으면 "이건 내가 고른 게 아니니 빼줘" 같은 조작을 붙일 수 없다.
--
-- 기존 행은 전부 사용자 선택이다. 서버가 후보를 채우는 기능이 생기기 전에 만들어진 일정이다.

ALTER TABLE plan_place
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'USER_PICKED';

ALTER TABLE plan_place
    ADD CONSTRAINT ck_plan_place_source CHECK (source IN ('USER_PICKED', 'AUTO_FILLED'));
