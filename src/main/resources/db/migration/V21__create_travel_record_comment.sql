CREATE TABLE travel_record_comment (
    id uuid NOT NULL,
    travel_record_id uuid NOT NULL,
    author_id uuid NOT NULL,
    content text NOT NULL,
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    CONSTRAINT travel_record_comment_pkey PRIMARY KEY (id),
    CONSTRAINT fk_travel_record_comment_record_id FOREIGN KEY (travel_record_id)
        REFERENCES travel_record (id) ON DELETE CASCADE,
    CONSTRAINT fk_travel_record_comment_author_id FOREIGN KEY (author_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_travel_record_comment_record_created_at
    ON travel_record_comment (travel_record_id, created_at ASC);
CREATE INDEX idx_travel_record_comment_author_id
    ON travel_record_comment (author_id);
