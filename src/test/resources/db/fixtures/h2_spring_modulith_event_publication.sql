create table if not exists event_publication (
    id uuid not null primary key,
    listener_id varchar(512) not null,
    event_type varchar(512) not null,
    serialized_event varchar(1000000) not null,
    publication_date timestamp with time zone not null,
    completion_date timestamp with time zone,
    status varchar(64),
    completion_attempts integer,
    last_resubmission_date timestamp with time zone
);

create index if not exists event_publication_serialized_event_hash_idx
    on event_publication(serialized_event);
create index if not exists event_publication_by_completion_date_idx
    on event_publication(completion_date);
