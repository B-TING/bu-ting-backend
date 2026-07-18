CREATE TABLE travel_member (
    id uuid NOT NULL,
    travel_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role varchar(20) NOT NULL,
    CONSTRAINT travel_member_pkey PRIMARY KEY (id),
    CONSTRAINT uk_travel_member_travel_user UNIQUE (travel_id, user_id),
    CONSTRAINT fk_travel_member_travel_id FOREIGN KEY (travel_id)
        REFERENCES travel (id) ON DELETE CASCADE,
    CONSTRAINT fk_travel_member_user_id FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE travel_invite (
    id uuid NOT NULL,
    travel_id uuid NOT NULL,
    token varchar(100) NOT NULL,
    used boolean NOT NULL,
    expired_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT travel_invite_pkey PRIMARY KEY (id),
    CONSTRAINT uk_travel_invite_token UNIQUE (token),
    CONSTRAINT fk_travel_invite_travel_id FOREIGN KEY (travel_id)
        REFERENCES travel (id) ON DELETE CASCADE
);

CREATE INDEX idx_travel_member_user_id ON travel_member (user_id);

CREATE INDEX idx_travel_member_travel_id_role ON travel_member (travel_id, role);

CREATE INDEX idx_travel_invite_travel_id ON travel_invite (travel_id);
