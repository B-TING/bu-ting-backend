-- V29 시드가 역 이름만으로 매핑(`WHERE s.name = ...`)해 생긴 락커 데이터 왜곡을 바로잡는다.
-- locker_location에는 유니크 제약이 없어 같은 시드를 두 번 돌린 것도 막히지 않았다.

-- 1) 완전 중복 제거. 같은 (역, 위치, 업체) 조합은 가장 먼저 만들어진 행만 남긴다.
CREATE TEMP TABLE locker_location_dupes ON COMMIT DROP AS
SELECT id
FROM (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY station_id, COALESCE(location_detail, ''), COALESCE(company, '')
               ORDER BY created_at, id
           ) AS rn
    FROM locker_location
) ranked
WHERE rn > 1;

DELETE FROM locker_fee f USING locker_location_dupes d WHERE f.locker_location_id = d.id;
DELETE FROM locker_location l USING locker_location_dupes d WHERE l.id = d.id;

-- 2) 환승역 중복 제거. 이름이 같은 역이 여러 노선에 있으면 물리적으로는 한 역사이므로
--    번호가 낮은 호선의 station 행에만 락커를 남긴다 (수영 2, 연산 1, 덕천 2, 미남 3, 서면 1).
CREATE TEMP TABLE locker_location_wrong_line ON COMMIT DROP AS
WITH shared_name AS (
    SELECT name FROM station GROUP BY name HAVING COUNT(*) > 1
), keeper AS (
    SELECT DISTINCT ON (s.name) s.name, s.id
    FROM station s
    JOIN shared_name n ON n.name = s.name
    ORDER BY s.name, s.line
)
SELECT l.id
FROM locker_location l
JOIN station s ON s.id = l.station_id
JOIN shared_name n ON n.name = s.name
WHERE l.station_id <> (SELECT k.id FROM keeper k WHERE k.name = s.name);

DELETE FROM locker_fee f USING locker_location_wrong_line w WHERE f.locker_location_id = w.id;
DELETE FROM locker_location l USING locker_location_wrong_line w WHERE l.id = w.id;

-- 3) 요금 오매핑 교정. 부산대양산캠퍼스 락커의 요금 세트(주말 SMALL 2775)가 역 이름 조인 탓에
--    남양산(범어)에 붙어, 정작 부산대양산캠퍼스 락커는 요금이 0건이었다.
DELETE FROM locker_fee f
USING locker_location l, station s
WHERE f.locker_location_id = l.id
  AND l.station_id = s.id
  AND s.name = '남양산(범어)'
  AND f.schedule_type = 'WEEKEND'
  AND f.locker_size = 'SMALL'
  AND f.amount = 2775;

INSERT INTO locker_fee (locker_location_id, schedule_type, locker_size, amount, billing_unit)
SELECT l.id, v.schedule_type, v.locker_size, v.amount, '기본'
FROM locker_location l
JOIN station s ON s.id = l.station_id
CROSS JOIN (VALUES
    ('WEEKDAY', 'EXTRA_LARGE', 6000),
    ('WEEKDAY', 'LARGE', 4500),
    ('WEEKDAY', 'SMALL', 2500),
    ('WEEKEND', 'EXTRA_LARGE', 6600),
    ('WEEKEND', 'LARGE', 4950),
    ('WEEKEND', 'SMALL', 2750)
) AS v(schedule_type, locker_size, amount)
WHERE s.name = '부산대양산캠퍼스'
ON CONFLICT (locker_location_id, schedule_type, locker_size, amount, billing_unit) DO NOTHING;

-- 4) 조작된 주말 SMALL 금액 정규화. 시드 CSV가 행을 유일하게 만들려고 2,750원부터 1원씩 올려
--    2,775원까지 넣었다. 실제 주말 요금은 평일 2,500원의 10% 할증인 2,750원이다
--    (특대 6,000→6,600, 대 4,500→4,950과 같은 비율).
-- 이름 조인 탓에 한 락커가 여러 주말 SMALL 요금을 갖고 있으면(예: 장산 2,750/2,752)
-- 정규화 시 유니크 제약에 걸리므로 가장 낮은 금액만 남긴다.
DELETE FROM locker_fee f
USING (
    SELECT locker_location_id, billing_unit, MIN(amount) AS keep_amount
    FROM locker_fee
    WHERE schedule_type = 'WEEKEND' AND locker_size = 'SMALL' AND amount BETWEEN 2750 AND 2775
    GROUP BY locker_location_id, billing_unit
) m
WHERE f.locker_location_id = m.locker_location_id
  AND f.billing_unit = m.billing_unit
  AND f.schedule_type = 'WEEKEND'
  AND f.locker_size = 'SMALL'
  AND f.amount BETWEEN 2750 AND 2775
  AND f.amount > m.keep_amount;

UPDATE locker_fee
SET amount = 2750, updated_at = CURRENT_TIMESTAMP
WHERE schedule_type = 'WEEKEND'
  AND locker_size = 'SMALL'
  AND amount BETWEEN 2751 AND 2775;

UPDATE locker_location
SET raw_fee_text = REGEXP_REPLACE(raw_fee_text, '소: 27[0-9][0-9]원 \(주말\)', '소: 2,750원 (주말)'),
    updated_at = CURRENT_TIMESTAMP
WHERE raw_fee_text ~ '소: 27[0-9][0-9]원 \(주말\)';

-- 5) 락커 정보가 없는 역에 자리표시 행을 넣어 조회 결과가 비지 않게 한다.
--    실제 보관함이 확인되지 않은 곳이므로 수량은 0이고 요금 행도 만들지 않는다.
--    2)에서 한쪽 호선으로 정리한 환승역은 같은 이름의 다른 호선 행에 락커가 있으므로 제외한다.
INSERT INTO locker_location (station_id, location_detail, small_count, medium_count, large_count, extra_large_count, company, raw_fee_text)
SELECT s.id, '(정보 준비중)', 0, 0, 0, 0, NULL, NULL
FROM station s
WHERE NOT EXISTS (
    SELECT 1
    FROM locker_location l
    JOIN station sibling ON sibling.id = l.station_id
    WHERE sibling.name = s.name
);

-- 6) 재발 방지. 같은 시드를 다시 돌려도 중복 행이 생기지 않는다.
--    location_detail/company가 NULL일 수 있어 COALESCE 식 인덱스를 쓴다.
CREATE UNIQUE INDEX uk_locker_location_station_detail_company
    ON locker_location (station_id, COALESCE(location_detail, ''), COALESCE(company, ''));
