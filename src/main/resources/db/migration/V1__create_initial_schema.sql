CREATE TABLE users (
    id uuid NOT NULL,
    email varchar(100),
    provider varchar(30),
    provider_id varchar(100),
    last_name varchar(20) NOT NULL,
    first_name varchar(50) NOT NULL,
    nickname varchar(50) NOT NULL,
    role varchar(20) NOT NULL,
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    created_by varchar(50),
    updated_by varchar(50),
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_provider_provider_id UNIQUE (provider, provider_id)
);

CREATE TABLE opaque_tokens (
    id uuid NOT NULL,
    token_hash varchar(64) NOT NULL,
    user_id uuid NOT NULL,
    expires_at timestamp(6) NOT NULL,
    revoked_at timestamp(6),
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    created_by varchar(50),
    updated_by varchar(50),
    CONSTRAINT opaque_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT uk_opaque_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_opaque_tokens_user_id FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_opaque_tokens_token_hash ON opaque_tokens (token_hash);
CREATE INDEX idx_opaque_tokens_user_id ON opaque_tokens (user_id);
