-- 인기도(평점·리뷰 수) 보강 시각. 전체 장소를 매번 외부 조회하지 않도록,
-- 아직 보강하지 않은 장소와 오래된 장소를 먼저 고르는 기준으로 쓴다.
-- Google 지도 데이터는 보관 기간 제약이 있어 만료 판단에도 필요하다.

ALTER TABLE place ADD COLUMN enriched_at TIMESTAMP;

CREATE INDEX idx_place_enriched_at ON place (enriched_at NULLS FIRST);
