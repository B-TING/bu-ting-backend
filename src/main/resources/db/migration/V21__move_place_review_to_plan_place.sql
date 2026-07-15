ALTER TABLE place_review
    ADD COLUMN plan_place_id uuid;

ALTER TABLE place_review
    ADD COLUMN author_id uuid;

ALTER TABLE place_review
    ALTER COLUMN travel_record_place_id DROP NOT NULL;

ALTER TABLE place_review
    ADD CONSTRAINT uk_place_review_plan_place_author UNIQUE (plan_place_id, author_id);

ALTER TABLE place_review
    ADD CONSTRAINT fk_place_review_plan_place_id
        FOREIGN KEY (plan_place_id)
        REFERENCES plan_place (id)
        ON DELETE CASCADE;

ALTER TABLE place_review
    ADD CONSTRAINT fk_place_review_author_id
        FOREIGN KEY (author_id)
        REFERENCES users (id)
        ON DELETE CASCADE;

ALTER TABLE place_review
    ADD CONSTRAINT chk_place_review_target
        CHECK (
            (plan_place_id IS NOT NULL AND author_id IS NOT NULL AND travel_record_place_id IS NULL)
            OR
            (plan_place_id IS NULL AND travel_record_place_id IS NOT NULL)
        );

CREATE INDEX idx_place_review_plan_place_id
    ON place_review (plan_place_id);

CREATE INDEX idx_place_review_author_id
    ON place_review (author_id);
