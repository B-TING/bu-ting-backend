CREATE TABLE travel_settlement (
    id uuid NOT NULL,
    travel_id uuid NOT NULL,
    confirmed_by_user_id uuid NOT NULL,
    confirmed_at timestamp(6) NOT NULL,
    CONSTRAINT travel_settlement_pkey PRIMARY KEY (id),
    CONSTRAINT uk_travel_settlement_travel_id UNIQUE (travel_id),
    CONSTRAINT fk_travel_settlement_travel_id FOREIGN KEY (travel_id)
        REFERENCES travel (id) ON DELETE CASCADE,
    CONSTRAINT fk_travel_settlement_confirmed_by_user_id FOREIGN KEY (confirmed_by_user_id)
        REFERENCES users (id)
);

CREATE TABLE travel_settlement_transfer (
    id uuid NOT NULL,
    settlement_id uuid NOT NULL,
    currency varchar(3) NOT NULL,
    from_user_id uuid NOT NULL,
    to_user_id uuid NOT NULL,
    amount bigint NOT NULL,
    CONSTRAINT travel_settlement_transfer_pkey PRIMARY KEY (id),
    CONSTRAINT chk_travel_settlement_transfer_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_travel_settlement_transfer_different_users CHECK (from_user_id <> to_user_id),
    CONSTRAINT fk_travel_settlement_transfer_settlement_id FOREIGN KEY (settlement_id)
        REFERENCES travel_settlement (id) ON DELETE CASCADE,
    CONSTRAINT fk_travel_settlement_transfer_from_user_id FOREIGN KEY (from_user_id)
        REFERENCES users (id),
    CONSTRAINT fk_travel_settlement_transfer_to_user_id FOREIGN KEY (to_user_id)
        REFERENCES users (id)
);

CREATE INDEX idx_travel_settlement_transfer_settlement_id
    ON travel_settlement_transfer (settlement_id);
