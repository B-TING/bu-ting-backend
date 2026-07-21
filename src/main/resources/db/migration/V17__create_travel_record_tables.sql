CREATE TABLE travel_record (
    id uuid NOT NULL,
    original_travel_id uuid,
    author_id uuid NOT NULL,
    title varchar(100) NOT NULL,
    content text,
    cover_image_url varchar(1000),
    travel_start_date date NOT NULL,
    travel_end_date date NOT NULL,
    status varchar(20) NOT NULL,
    published_at timestamp(6),
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    CONSTRAINT travel_record_pkey PRIMARY KEY (id),
    CONSTRAINT uk_travel_record_travel_author UNIQUE (original_travel_id, author_id),
    CONSTRAINT fk_travel_record_original_travel_id FOREIGN KEY (original_travel_id)
        REFERENCES travel (id) ON DELETE SET NULL,
    CONSTRAINT fk_travel_record_author_id FOREIGN KEY (author_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_travel_record_date_range CHECK (travel_end_date >= travel_start_date),
    CONSTRAINT chk_travel_record_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN')),
    CONSTRAINT chk_travel_record_published_at CHECK (
        (status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR (status <> 'PUBLISHED')
    )
);

CREATE TABLE travel_record_day (
    id uuid NOT NULL,
    travel_record_id uuid NOT NULL,
    original_plan_id uuid,
    day_number integer NOT NULL,
    visit_date date NOT NULL,
    CONSTRAINT travel_record_day_pkey PRIMARY KEY (id),
    CONSTRAINT uk_travel_record_day_number UNIQUE (travel_record_id, day_number),
    CONSTRAINT fk_travel_record_day_record_id FOREIGN KEY (travel_record_id)
        REFERENCES travel_record (id) ON DELETE CASCADE,
    CONSTRAINT chk_travel_record_day_number CHECK (day_number > 0)
);

CREATE TABLE travel_record_place (
    id uuid NOT NULL,
    travel_record_day_id uuid NOT NULL,
    original_plan_place_id uuid,
    sequence integer NOT NULL,
    place_name varchar(255) NOT NULL,
    address varchar(255) NOT NULL,
    latitude float(53),
    longitude float(53),
    provider varchar(20) NOT NULL,
    provider_place_id varchar(255) NOT NULL,
    duration_minutes integer,
    memo text,
    scheduled_time time(6),
    is_visited boolean NOT NULL,
    CONSTRAINT travel_record_place_pkey PRIMARY KEY (id),
    CONSTRAINT uk_travel_record_place_sequence UNIQUE (travel_record_day_id, sequence),
    CONSTRAINT fk_travel_record_place_day_id FOREIGN KEY (travel_record_day_id)
        REFERENCES travel_record_day (id) ON DELETE CASCADE,
    CONSTRAINT chk_travel_record_place_sequence CHECK (sequence > 0),
    CONSTRAINT chk_travel_record_place_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_travel_record_place_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_travel_record_place_duration CHECK (duration_minutes IS NULL OR duration_minutes >= 0)
);

CREATE TABLE travel_record_route (
    id uuid NOT NULL,
    travel_record_day_id uuid NOT NULL,
    from_travel_record_place_id uuid NOT NULL,
    to_travel_record_place_id uuid NOT NULL,
    transport_type varchar(30) NOT NULL,
    duration_minutes integer,
    distance_meters integer,
    provider varchar(20),
    calculated_at timestamp(6) with time zone,
    CONSTRAINT travel_record_route_pkey PRIMARY KEY (id),
    CONSTRAINT uk_travel_record_route_from_to UNIQUE (
        travel_record_day_id,
        from_travel_record_place_id,
        to_travel_record_place_id
    ),
    CONSTRAINT fk_travel_record_route_day_id FOREIGN KEY (travel_record_day_id)
        REFERENCES travel_record_day (id) ON DELETE CASCADE,
    CONSTRAINT fk_travel_record_route_from_place_id FOREIGN KEY (from_travel_record_place_id)
        REFERENCES travel_record_place (id) ON DELETE CASCADE,
    CONSTRAINT fk_travel_record_route_to_place_id FOREIGN KEY (to_travel_record_place_id)
        REFERENCES travel_record_place (id) ON DELETE CASCADE,
    CONSTRAINT chk_travel_record_route_duration CHECK (duration_minutes IS NULL OR duration_minutes >= 0),
    CONSTRAINT chk_travel_record_route_distance CHECK (distance_meters IS NULL OR distance_meters >= 0)
);

CREATE TABLE place_review (
    id uuid NOT NULL,
    travel_record_place_id uuid NOT NULL,
    rating integer NOT NULL,
    content text,
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    CONSTRAINT place_review_pkey PRIMARY KEY (id),
    CONSTRAINT uk_place_review_record_place UNIQUE (travel_record_place_id),
    CONSTRAINT fk_place_review_record_place_id FOREIGN KEY (travel_record_place_id)
        REFERENCES travel_record_place (id) ON DELETE CASCADE,
    CONSTRAINT chk_place_review_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE TABLE travel_record_image (
    id uuid NOT NULL,
    travel_record_id uuid NOT NULL,
    url varchar(1000) NOT NULL,
    sequence integer NOT NULL,
    CONSTRAINT travel_record_image_pkey PRIMARY KEY (id),
    CONSTRAINT uk_travel_record_image_sequence UNIQUE (travel_record_id, sequence),
    CONSTRAINT fk_travel_record_image_record_id FOREIGN KEY (travel_record_id)
        REFERENCES travel_record (id) ON DELETE CASCADE,
    CONSTRAINT chk_travel_record_image_sequence CHECK (sequence > 0)
);

CREATE TABLE place_review_image (
    id uuid NOT NULL,
    place_review_id uuid NOT NULL,
    url varchar(1000) NOT NULL,
    sequence integer NOT NULL,
    CONSTRAINT place_review_image_pkey PRIMARY KEY (id),
    CONSTRAINT uk_place_review_image_sequence UNIQUE (place_review_id, sequence),
    CONSTRAINT fk_place_review_image_review_id FOREIGN KEY (place_review_id)
        REFERENCES place_review (id) ON DELETE CASCADE,
    CONSTRAINT chk_place_review_image_sequence CHECK (sequence > 0)
);

CREATE INDEX idx_travel_record_author_id ON travel_record (author_id);
CREATE INDEX idx_travel_record_status_published_at ON travel_record (status, published_at DESC);
CREATE INDEX idx_travel_record_original_travel_id ON travel_record (original_travel_id);

CREATE INDEX idx_travel_record_day_record_day ON travel_record_day (travel_record_id, day_number);

CREATE INDEX idx_travel_record_place_day_sequence ON travel_record_place (travel_record_day_id, sequence);
CREATE INDEX idx_travel_record_place_provider_place_id
    ON travel_record_place (provider, provider_place_id);

CREATE INDEX idx_travel_record_route_day_id ON travel_record_route (travel_record_day_id);
CREATE INDEX idx_travel_record_route_from_place_id ON travel_record_route (from_travel_record_place_id);
CREATE INDEX idx_travel_record_route_to_place_id ON travel_record_route (to_travel_record_place_id);

CREATE INDEX idx_place_review_record_place_id ON place_review (travel_record_place_id);

CREATE INDEX idx_travel_record_image_record_sequence
    ON travel_record_image (travel_record_id, sequence);
CREATE INDEX idx_place_review_image_review_sequence
    ON place_review_image (place_review_id, sequence);
