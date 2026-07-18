CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE locker_location (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id VARCHAR(100) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    subway_line INTEGER NOT NULL,
    location_detail TEXT,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    small_count INTEGER NOT NULL DEFAULT 0,
    medium_count INTEGER NOT NULL DEFAULT 0,
    large_count INTEGER NOT NULL DEFAULT 0,
    extra_large_count INTEGER NOT NULL DEFAULT 0,
    company VARCHAR(100),
    raw_fee_text TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT uk_locker_location_source_external UNIQUE (source_type, external_id)
);

CREATE TABLE locker_fee (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    locker_location_id UUID NOT NULL REFERENCES locker_location(id),
    source_type VARCHAR(50) NOT NULL,
    external_id VARCHAR(100) NOT NULL,
    schedule_type VARCHAR(50) NOT NULL,
    locker_size VARCHAR(30) NOT NULL,
    amount INTEGER NOT NULL CHECK (amount >= 0),
    billing_unit VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT uk_locker_fee_unique UNIQUE
      (locker_location_id, schedule_type, locker_size, amount, billing_unit)
);

CREATE INDEX idx_locker_location_latitude_longitude
  ON locker_location (latitude, longitude);
CREATE INDEX idx_locker_fee_location
  ON locker_fee (locker_location_id);
