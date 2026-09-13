-- 제출 fileKey 재사용 방지를 DB 레벨에서 강제한다(동시 요청 TOCTOU 방지).
-- 부수 효과로 existsByMediaFileKey 조회가 순차 스캔 대신 인덱스 탐색이 된다.
CREATE UNIQUE INDEX uk_zone_event_submission_media_file_key
    ON zone_event_submission (media_file_key);
