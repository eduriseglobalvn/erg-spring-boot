-- Captured mutations wait here (written without the audit_events tenant lock) until AuditOutboxRelay
-- appends them to the hash-chained audit_events. The existing outbox_events dispatcher rejects unknown
-- event types, so audit gets its own table.
create table if not exists audit_outbox (
    id varchar(64) primary key,
    tenant_id varchar(96) not null,
    payload jsonb not null,
    status varchar(16) not null default 'pending',
    attempts integer not null default 0,
    available_at timestamptz not null default now(),
    lease_until timestamptz,
    last_error text,
    created_at timestamptz not null default now(),
    constraint ck_audit_outbox_status check (status in ('pending', 'processing', 'dead'))
);

create index if not exists idx_audit_outbox_pickup on audit_outbox (status, available_at, created_at);

alter table audit_outbox enable row level security;
drop policy if exists audit_outbox_tenant_isolation on audit_outbox;
create policy audit_outbox_tenant_isolation on audit_outbox
    using (current_setting('app.maintenance_mode', true) = 'true' or tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    with check (current_setting('app.maintenance_mode', true) = 'true' or tenant_id = nullif(current_setting('app.tenant_id', true), ''));
