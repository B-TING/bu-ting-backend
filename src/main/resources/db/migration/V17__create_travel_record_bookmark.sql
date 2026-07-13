CREATE TABLE travel_record_bookmark (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    travel_record_id uuid NOT NULL,
    created_at timestamp(6) NOT NULL,
    CONSTRAINT travel_record_bookmark_pkey PRIMARY KEY (id),
    CONSTRAINT uk_travel_record_bookmark_user_record UNIQUE (user_id, travel_record_id),
    CONSTRAINT fk_travel_record_bookmark_user_id FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_travel_record_bookmark_record_id FOREIGN KEY (travel_record_id)
        REFERENCES travel_record (id) ON DELETE CASCADE
);

CREATE INDEX idx_travel_record_bookmark_user_created_at
    ON travel_record_bookmark (user_id, created_at DESC);
CREATE INDEX idx_travel_record_bookmark_record_id
    ON travel_record_bookmark (travel_record_id);
