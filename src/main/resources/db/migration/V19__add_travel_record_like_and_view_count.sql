ALTER TABLE travel_record
    ADD COLUMN like_count bigint NOT NULL DEFAULT 0,
    ADD COLUMN view_count bigint NOT NULL DEFAULT 0;

CREATE TABLE travel_record_like (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    travel_record_id uuid NOT NULL,
    created_at timestamp(6) NOT NULL,
    CONSTRAINT travel_record_like_pkey PRIMARY KEY (id),
    CONSTRAINT uk_travel_record_like_user_record UNIQUE (user_id, travel_record_id),
    CONSTRAINT fk_travel_record_like_user_id FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_travel_record_like_record_id FOREIGN KEY (travel_record_id)
        REFERENCES travel_record (id) ON DELETE CASCADE
);

CREATE INDEX idx_travel_record_like_user_id
    ON travel_record_like (user_id);
CREATE INDEX idx_travel_record_like_record_id
    ON travel_record_like (travel_record_id);
CREATE INDEX idx_travel_record_like_record_created_at
    ON travel_record_like (travel_record_id, created_at DESC);
