CREATE TABLE travel (
    id uuid NOT NULL,
    title varchar(15),
    start_date date NOT NULL,
    end_date date NOT NULL,
    status varchar(30) NOT NULL,
    created_at timestamp(6) NOT NULL,
    has_heavy_baggage boolean,
    has_pets boolean,
    travel_style varchar(30),
    prefer_flat_terrain boolean,
    pace varchar(30),
    companion_count integer,
    preferred_foods varchar(255),
    companion_types varchar(30),
    accommodation_area varchar(255),
    CONSTRAINT travel_pkey PRIMARY KEY (id),
    CONSTRAINT chk_travel_date_range CHECK (end_date >= start_date)
);

CREATE TABLE plan (
    id uuid NOT NULL,
    travel_id uuid NOT NULL,
    day_number integer NOT NULL,
    visit_date date,
    CONSTRAINT plan_pkey PRIMARY KEY (id),
    CONSTRAINT fk_plan_travel_id FOREIGN KEY (travel_id)
        REFERENCES travel (id) ON DELETE CASCADE,
    CONSTRAINT chk_plan_day_number CHECK (day_number > 0)
);

CREATE TABLE plan_place (
    id uuid NOT NULL,
    plan_id uuid NOT NULL,
    google_place_id varchar(255) NOT NULL,
    place_name varchar(255),
    sequence integer,
    duration_time time(6),
    transport_type varchar(30),
    transport_duration time(6),
    is_visited boolean,
    latitude float(53),
    longitude float(53),
    addr varchar(255),
    CONSTRAINT plan_place_pkey PRIMARY KEY (id),
    CONSTRAINT uk_plan_place_google_place_id UNIQUE (google_place_id),
    CONSTRAINT fk_plan_place_plan_id FOREIGN KEY (plan_id)
        REFERENCES plan (id) ON DELETE CASCADE,
    CONSTRAINT chk_plan_place_sequence CHECK (sequence IS NULL OR sequence > 0),
    CONSTRAINT chk_plan_place_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_plan_place_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_plan_travel_id_day_number ON plan (travel_id, day_number);

CREATE INDEX idx_plan_place_plan_id_sequence ON plan_place (plan_id, sequence);
