ALTER TABLE plan_place
    ADD COLUMN content_type_id VARCHAR(10);

-- 기존 AUTO_FILLED / TourAPI 카탈로그 장소는 contentId(=provider_place_id)로 유형을 채운다.
UPDATE plan_place pp
SET content_type_id = p.content_type_id
FROM place p
WHERE pp.content_type_id IS NULL
  AND p.provider = 'TOUR_API'
  AND p.provider_place_id = pp.provider_place_id
  AND p.content_type_id IS NOT NULL;
