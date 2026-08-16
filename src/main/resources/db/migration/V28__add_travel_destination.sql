ALTER TABLE travel ADD COLUMN destination varchar(100);

UPDATE travel SET destination = COALESCE(NULLIF(accommodation_area, ''), title, '미정')
WHERE destination IS NULL;
