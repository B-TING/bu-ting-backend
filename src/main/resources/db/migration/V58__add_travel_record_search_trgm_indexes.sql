-- 여행기 피드 검색은 lower(컬럼) LIKE '%키워드%' 형태라 B-tree 인덱스를 못 탄다.
-- 앞에 와일드카드가 붙으면 정렬 순서로 범위를 좁힐 수 없어 매번 전체 스캔이다.
-- pg_trgm GIN 인덱스는 문자열을 3글자 조각으로 쪼개 색인하므로 중간 일치도 탄다.
--
-- 인덱스 식은 쿼리가 쓰는 식과 글자 그대로 같아야 한다. TravelRecordRepository 의
-- 피드 쿼리가 lower(...) 와 coalesce(..., '') 를 쓰므로 여기서도 똑같이 쓴다.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_travel_record_title_trgm
    ON travel_record USING gin (lower(title) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_travel_record_content_trgm
    ON travel_record USING gin (lower(coalesce(content, '')) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_travel_record_place_name_trgm
    ON travel_record_place USING gin (lower(place_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_travel_record_place_address_trgm
    ON travel_record_place USING gin (lower(coalesce(address, '')) gin_trgm_ops);
