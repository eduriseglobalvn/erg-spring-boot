-- Durable outbox for post-commit external effects. Notification delivery keeps its
-- existing specialized table; this table covers SMTP and future providers.
-- claim path for outbox_events is implemented with FOR UPDATE SKIP LOCKED.
create table if not exists outbox_events (
    id varchar(96) primary key,
    tenant_id varchar(96) not null,
    event_type varchar(96) not null,
    aggregate_id varchar(96),
    idempotency_key varchar(192) not null,
    payload jsonb not null,
    status varchar(24) not null default 'pending',
    attempts integer not null default 0,
    available_at timestamptz not null default now(),
    lease_until timestamptz,
    last_error text,
    created_at timestamptz not null default now(),
    completed_at timestamptz,
    constraint uq_outbox_events_tenant_idempotency unique (tenant_id, idempotency_key),
    constraint ck_outbox_events_status check (status in ('pending', 'processing', 'retry', 'sent', 'dead'))
);

create index if not exists idx_outbox_events_pickup
    on outbox_events(status, available_at, lease_until, created_at);
create index if not exists idx_outbox_events_tenant_pickup
    on outbox_events(tenant_id, status, available_at, created_at);
