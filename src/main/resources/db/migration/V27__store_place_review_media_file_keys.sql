ALTER TABLE place_review_image
    ADD COLUMN file_key varchar(500);

ALTER TABLE place_review_image
    RENAME COLUMN url TO external_url;

ALTER TABLE place_review_image
    ALTER COLUMN external_url DROP NOT NULL;

UPDATE place_review_image
SET file_key = substring(split_part(external_url, '?', 1) FROM 'amazonaws\\.com/(.*)$')
WHERE external_url LIKE '%amazonaws.com/%';

UPDATE place_review_image
SET external_url = NULL
WHERE file_key IS NOT NULL;

ALTER TABLE place_review_image
    ADD CONSTRAINT chk_place_review_image_media_source
    CHECK (
        (file_key IS NOT NULL AND external_url IS NULL)
        OR (file_key IS NULL AND external_url IS NOT NULL)
    );
