-- Archiving a school (DELETE /schools/{id}) archives its open classes and students and disables the
-- students' login accounts in one transaction. Each row here is one thing that archive changed, with
-- the status it had before, so restoring the school reopens exactly that and nothing archived or
-- locked on its own. Rows are removed when the school is restored. A paused school changes nothing
-- here: its students keep logging in (owner decision, 19/11/2026).
create table if not exists school_archive_holds (
    tenant_id varchar(96) not null,
    school_id varchar(64) not null,
    entity_type varchar(16) not null,
    entity_id varchar(64) not null,
    previous_status varchar(32),
    held_at timestamptz not null default now(),
    primary key (entity_type, entity_id),
    constraint ck_school_archive_holds_type check (entity_type in ('CLASS', 'STUDENT', 'USER'))
);

create index if not exists idx_school_archive_holds_school on school_archive_holds (tenant_id, school_id);

alter table school_archive_holds enable row level security;
drop policy if exists school_archive_holds_tenant_isolation on school_archive_holds;
create policy school_archive_holds_tenant_isolation on school_archive_holds
    using (current_setting('app.maintenance_mode', true) = 'true' or tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    with check (current_setting('app.maintenance_mode', true) = 'true' or tenant_id = nullif(current_setting('app.tenant_id', true), ''));
