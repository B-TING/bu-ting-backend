CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE station (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operator VARCHAR(50) NOT NULL,
    line VARCHAR(30) NOT NULL,
    name VARCHAR(50) NOT NULL,
    longitude NUMERIC(10, 7) NOT NULL,
    latitude NUMERIC(10, 7) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_station_name ON station (name);

CREATE TABLE locker_location (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    station_id UUID NOT NULL REFERENCES station(id),
    location_detail TEXT,
    small_count INTEGER NOT NULL DEFAULT 0,
    medium_count INTEGER NOT NULL DEFAULT 0,
    large_count INTEGER NOT NULL DEFAULT 0,
    extra_large_count INTEGER NOT NULL DEFAULT 0,
    company VARCHAR(100),
    raw_fee_text TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_locker_location_station_id
  ON locker_location (station_id);

CREATE TABLE locker_fee (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    locker_location_id UUID NOT NULL REFERENCES locker_location(id),
    schedule_type VARCHAR(50) NOT NULL,
    locker_size VARCHAR(30) NOT NULL,
    amount INTEGER NOT NULL CHECK (amount >= 0),
    billing_unit VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_locker_fee_unique
      UNIQUE (locker_location_id, schedule_type, locker_size, amount, billing_unit)
);

CREATE INDEX idx_locker_fee_location
  ON locker_fee (locker_location_id);
