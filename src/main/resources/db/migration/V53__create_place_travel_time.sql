-- 외부 경로 API가 준 구간 소요 시간 캐시.
-- 일정 생성이 날짜마다 N² 구간을 조회하므로 캐시 없이는 외부 호출이 그대로 늘어난다.
--
-- 선적재하지 않는다. 장소가 수천 개면 쌍이 수백만인데 실제로 쓰이는 조합은 소수에 몰린다.
-- 좌표 계산(Haversine) 결과는 담지 않는다. 계산이 조회보다 싸다.
--
-- 키는 장소 식별자가 아니라 반올림한 좌표다. 경로 계산에는 사용자의 현재 위치처럼
-- 카탈로그에 없는 지점도 들어오기 때문이다.

CREATE TABLE place_travel_time (
    from_key         VARCHAR(40) NOT NULL,
    to_key           VARCHAR(40) NOT NULL,
    transport_type   VARCHAR(30) NOT NULL,
    duration_minutes INTEGER NOT NULL,
    distance_meters  INTEGER NOT NULL,
    fetched_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (from_key, to_key, transport_type),
    CONSTRAINT ck_place_travel_time_duration CHECK (duration_minutes >= 0),
    CONSTRAINT ck_place_travel_time_distance CHECK (distance_meters >= 0)
);

-- 만료 정리를 위한 조회 경로.
CREATE INDEX idx_place_travel_time_fetched_at ON place_travel_time (fetched_at);
