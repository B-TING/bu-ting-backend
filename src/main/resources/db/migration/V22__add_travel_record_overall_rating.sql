ALTER TABLE travel_record
    ADD COLUMN overall_rating integer;

ALTER TABLE travel_record
    ADD CONSTRAINT chk_travel_record_overall_rating
        CHECK (overall_rating IS NULL OR overall_rating BETWEEN 1 AND 5);
