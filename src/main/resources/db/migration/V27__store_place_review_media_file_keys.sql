UPDATE place_review_image
SET url = substring(split_part(url, '?', 1) FROM 'amazonaws\\.com/(.*)$')
WHERE url LIKE '%amazonaws.com/%';

ALTER TABLE place_review_image
    RENAME COLUMN url TO file_key;

ALTER TABLE place_review_image
    ALTER COLUMN file_key TYPE varchar(500);
