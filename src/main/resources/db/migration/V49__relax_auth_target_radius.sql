-- 인증 타겟 반경 상한을 500m에서 2000m로 완화한다. 센텀시티처럼 대상 지형이 넓은 장소는
-- 500m로 현장 범위를 담지 못한다. 하한 30m는 그대로 둔다(상한 완화이므로 기존 행은 전부 유효).

ALTER TABLE zone_event_auth_target
    DROP CONSTRAINT ck_zone_event_auth_target_radius,
    ADD CONSTRAINT ck_zone_event_auth_target_radius CHECK (radius_m BETWEEN 30 AND 2000);

ALTER TABLE zone_event_backup_target
    DROP CONSTRAINT ck_zone_event_backup_target_radius,
    ADD CONSTRAINT ck_zone_event_backup_target_radius CHECK (radius_m BETWEEN 30 AND 2000);
