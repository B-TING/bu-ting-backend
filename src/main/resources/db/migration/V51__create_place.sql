-- 장소 카탈로그. 지금까지 장소는 서버에 저장하지 않고 TourAPI/Google Places를 요청마다 호출했고,
-- 앱이 쓰는 목록은 모바일 상수에 하드코딩돼 있었다. 적재는 운영 동기화 API가 맡고 여기서는 표만 만든다.
--
-- (provider, provider_place_id)는 AI 일정 생성의 PlaceKey와 같은 식별 체계라 기존 코드에 그대로 접합된다.
-- zone_id는 TourAPI의 lDongSignguCd를 ChatZone으로 매핑해 적재 시점에 채운다.

CREATE TABLE place (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider            VARCHAR(20)  NOT NULL,
    provider_place_id   VARCHAR(64)  NOT NULL,
    name                VARCHAR(200) NOT NULL,
    address             VARCHAR(300),
    latitude            DOUBLE PRECISION,
    longitude           DOUBLE PRECISION,
    content_type_id     VARCHAR(10),
    image_url           VARCHAR(500),
    zone_id             VARCHAR(30),
    district_code       VARCHAR(10),
    -- 아래 넷은 후속 이슈(#270, #271)에서 채운다.
    dwell_minutes       INTEGER,
    rating              NUMERIC(2, 1),
    review_count        INTEGER,
    preferred_time_slot VARCHAR(20),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_place_provider_place_id UNIQUE (provider, provider_place_id),
    CONSTRAINT ck_place_zone_id CHECK (zone_id IS NULL OR zone_id IN (
        'HAEUNDAE_GIJANG', 'SUYEONG_NAMGU', 'CENTRAL_NORTH',
        'OLD_DOWNTOWN', 'YEONGDO', 'WESTERN_BUSAN')),
    CONSTRAINT ck_place_time_slot CHECK (preferred_time_slot IS NULL OR preferred_time_slot IN (
        'MORNING', 'AFTERNOON', 'EVENING')),
    CONSTRAINT ck_place_dwell_minutes CHECK (dwell_minutes IS NULL OR dwell_minutes > 0),
    CONSTRAINT ck_place_review_count CHECK (review_count IS NULL OR review_count >= 0)
);

-- 권역별 후보 조회가 주 조회 경로다.
CREATE INDEX idx_place_zone_id ON place (zone_id);
CREATE INDEX idx_place_content_type_id ON place (content_type_id);
