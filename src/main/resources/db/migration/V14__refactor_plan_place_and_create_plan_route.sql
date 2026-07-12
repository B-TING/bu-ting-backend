ALTER TABLE plan
    ADD CONSTRAINT uk_plan_travel_day UNIQUE (travel_id, day_number);

ALTER TABLE plan
    ADD CONSTRAINT uk_plan_travel_visit_date UNIQUE (travel_id, visit_date);

ALTER TABLE plan
    ALTER COLUMN visit_date SET NOT NULL;

ALTER TABLE plan_place
    DROP CONSTRAINT IF EXISTS uk_plan_place_google_place_id;

ALTER TABLE plan_place
    ADD COLUMN address varchar(255),
    ADD COLUMN provider varchar(20),
    ADD COLUMN provider_place_id varchar(255),
    ADD COLUMN duration_minutes integer;

UPDATE plan_place
SET address = addr,
    provider = 'GOOGLE',
    provider_place_id = google_place_id
WHERE provider_place_id IS NULL;

ALTER TABLE plan_place
    ALTER COLUMN sequence SET NOT NULL,
    ALTER COLUMN place_name SET NOT NULL,
    ALTER COLUMN address SET NOT NULL,
    ALTER COLUMN provider SET NOT NULL,
    ALTER COLUMN provider_place_id SET NOT NULL,
    ALTER COLUMN is_visited SET NOT NULL;

ALTER TABLE plan_place
    DROP COLUMN google_place_id,
    DROP COLUMN duration_time,
    DROP COLUMN transport_type,
    DROP COLUMN transport_duration,
    DROP COLUMN addr;

ALTER TABLE plan_place
    ADD CONSTRAINT uk_plan_place_sequence UNIQUE (plan_id, sequence);

CREATE TABLE plan_route (
    id uuid NOT NULL,
    plan_id uuid NOT NULL,
    from_plan_place_id uuid NOT NULL,
    to_plan_place_id uuid NOT NULL,
    transport_type varchar(30) NOT NULL,
    duration_minutes integer,
    distance_meters integer,
    provider varchar(20),
    calculated_at timestamp(6) with time zone,
    CONSTRAINT plan_route_pkey PRIMARY KEY (id),
    CONSTRAINT uk_plan_route_from_to UNIQUE (plan_id, from_plan_place_id, to_plan_place_id),
    CONSTRAINT fk_plan_route_plan_id FOREIGN KEY (plan_id)
        REFERENCES plan (id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_route_from_plan_place_id FOREIGN KEY (from_plan_place_id)
        REFERENCES plan_place (id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_route_to_plan_place_id FOREIGN KEY (to_plan_place_id)
        REFERENCES plan_place (id) ON DELETE CASCADE,
    CONSTRAINT chk_plan_route_duration CHECK (duration_minutes IS NULL OR duration_minutes >= 0),
    CONSTRAINT chk_plan_route_distance CHECK (distance_meters IS NULL OR distance_meters >= 0)
);

CREATE INDEX idx_plan_route_plan_id ON plan_route (plan_id);

CREATE INDEX idx_plan_route_from_place_id ON plan_route (from_plan_place_id);

CREATE INDEX idx_plan_route_to_place_id ON plan_route (to_plan_place_id);
