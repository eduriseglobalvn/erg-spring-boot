-- Durable report jobs keep heavy exports off the interactive request path.
-- The source of truth remains the normalized LMS read model; result_json is
-- bounded by the worker and may be moved to object storage later.
create table if not exists report_jobs (
    id varchar(96) primary key,
    tenant_id varchar(96) not null,
    requested_by varchar(96) not null,
    report_type varchar(64) not null,
    aggregate_id varchar(96) not null,
    idempotency_key varchar(192) not null,
    request_json jsonb not null default '{}'::jsonb,
    result_json jsonb,
    status varchar(24) not null default 'queued',
    progress integer not null default 0,
    attempts integer not null default 0,
    available_at timestamptz not null default now(),
    lease_until timestamptz,
    last_error text,
    created_at timestamptz not null default now(),
    started_at timestamptz,
    completed_at timestamptz,
    constraint uq_report_jobs_tenant_idempotency unique (tenant_id, idempotency_key),
    constraint ck_report_jobs_status check (status in ('queued', 'processing', 'completed', 'failed')),
    constraint ck_report_jobs_progress check (progress between 0 and 100)
);

create index if not exists idx_report_jobs_pickup
    on report_jobs(status, available_at, lease_until, created_at);
create index if not exists idx_report_jobs_tenant_lookup
    on report_jobs(tenant_id, id, created_at);

alter table report_jobs enable row level security;
alter table report_jobs force row level security;
drop policy if exists report_jobs_tenant_isolation on report_jobs;
create policy report_jobs_tenant_isolation on report_jobs
    using (
        current_setting('app.maintenance_mode', true) = 'true'
        or tenant_id = nullif(current_setting('app.tenant_id', true), '')
    )
    with check (
        current_setting('app.maintenance_mode', true) = 'true'
        or tenant_id = nullif(current_setting('app.tenant_id', true), '')
    );
