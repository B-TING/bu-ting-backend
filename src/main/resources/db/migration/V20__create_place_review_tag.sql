create table place_review_tag (
    sequence integer not null,
    place_review_id uuid not null,
    tag varchar(30) not null,
    primary key (sequence, place_review_id),
    constraint fk_place_review_tag_review
        foreign key (place_review_id)
        references place_review (id)
        on delete cascade
);
