-- Phase 1 event types. Both require an uploaded photo for authentication.
-- Phase 3 will add MUKJJIPPA and PEDOMETER (upload not required).

INSERT INTO zone_event_type (type_code, name, requires_upload, description) VALUES
    ('PLACE_AUTH', '장소 인증', TRUE, '지정한 장소에서 사진을 찍어 인증한다.'),
    ('OBJECT_AUTH', '사물 인증', TRUE, '지정한 사물을 사진으로 찾아 인증한다.')
ON CONFLICT (type_code) DO NOTHING;
