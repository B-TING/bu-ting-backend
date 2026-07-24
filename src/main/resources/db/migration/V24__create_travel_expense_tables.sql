CREATE TABLE travel_expense (
    id uuid NOT NULL,
    travel_id uuid NOT NULL,
    title varchar(50) NOT NULL,
    amount bigint NOT NULL,
    currency varchar(3) NOT NULL DEFAULT 'KRW',
    category varchar(30) NOT NULL,
    payer_user_id uuid NOT NULL,
    created_by_user_id uuid NOT NULL,
    spent_at timestamp(6) NOT NULL,
    memo varchar(500),
    split_type varchar(20) NOT NULL,
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    CONSTRAINT travel_expense_pkey PRIMARY KEY (id),
    CONSTRAINT chk_travel_expense_amount_positive CHECK (amount > 0),
    CONSTRAINT fk_travel_expense_travel_id FOREIGN KEY (travel_id)
        REFERENCES travel (id) ON DELETE CASCADE,
    CONSTRAINT fk_travel_expense_payer_user_id FOREIGN KEY (payer_user_id)
        REFERENCES users (id),
    CONSTRAINT fk_travel_expense_created_by_user_id FOREIGN KEY (created_by_user_id)
        REFERENCES users (id)
);

CREATE TABLE travel_expense_share (
    id uuid NOT NULL,
    expense_id uuid NOT NULL,
    user_id uuid NOT NULL,
    share_amount bigint NOT NULL,
    CONSTRAINT travel_expense_share_pkey PRIMARY KEY (id),
    CONSTRAINT uk_travel_expense_share_expense_user UNIQUE (expense_id, user_id),
    CONSTRAINT chk_travel_expense_share_amount_non_negative CHECK (share_amount >= 0),
    CONSTRAINT fk_travel_expense_share_expense_id FOREIGN KEY (expense_id)
        REFERENCES travel_expense (id) ON DELETE CASCADE,
    CONSTRAINT fk_travel_expense_share_user_id FOREIGN KEY (user_id)
        REFERENCES users (id)
);

CREATE INDEX idx_travel_expense_travel_spent_at
    ON travel_expense (travel_id, spent_at DESC);

CREATE INDEX idx_travel_expense_travel_category
    ON travel_expense (travel_id, category);

CREATE INDEX idx_travel_expense_payer_user_id
    ON travel_expense (payer_user_id);

CREATE INDEX idx_travel_expense_share_user_id
    ON travel_expense_share (user_id);
