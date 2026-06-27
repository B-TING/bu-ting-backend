CREATE TABLE travel_survey (
    user_id uuid NOT NULL,
    preferred_language varchar(5) NOT NULL DEFAULT 'ko',
    is_planned boolean,
    is_relaxed boolean,
    is_solo boolean,
    is_light boolean,
    is_familiar boolean,
    purposes varchar(30)[] DEFAULT '{}'::varchar[],
    skipped_steps integer[] NOT NULL DEFAULT '{}'::integer[],
    skipped_all boolean NOT NULL DEFAULT false,
    completed_at timestamp(6),
    ai_prompt_context text,
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    created_by varchar(50),
    updated_by varchar(50),
    CONSTRAINT travel_survey_pkey PRIMARY KEY (user_id),
    CONSTRAINT fk_travel_survey_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_skipped_all_consistency CHECK (
        (
            skipped_all = true
            AND is_planned IS NULL
            AND is_relaxed IS NULL
            AND is_solo IS NULL
            AND is_light IS NULL
            AND is_familiar IS NULL
        )
        OR skipped_all = false
    )
);

CREATE INDEX idx_travel_survey_lang_completed
    ON travel_survey (preferred_language, completed_at DESC);

CREATE INDEX idx_travel_survey_purposes
    ON travel_survey USING gin (purposes);
