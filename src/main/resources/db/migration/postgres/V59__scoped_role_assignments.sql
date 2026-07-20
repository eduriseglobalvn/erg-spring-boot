create table if not exists user_role_assignments (
    id varchar(36) primary key,
    user_id varchar(24) not null,
    role_id varchar(24) not null,
    tenant_id varchar(36) not null,
    scope_type varchar(32) not null,
    scope_id varchar(36),
    expires_at timestamptz,
    assigned_by varchar(24) not null,
    revoked_at timestamptz,
    revoked_by varchar(24),
    revoke_reason varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_user_role_assignments_user foreign key (user_id) references users(id) on delete cascade,
    constraint fk_user_role_assignments_role foreign key (role_id) references roles(id) on delete cascade,
    constraint fk_user_role_assignments_actor foreign key (assigned_by) references users(id),
    constraint fk_user_role_assignments_revoker foreign key (revoked_by) references users(id),
    constraint ck_user_role_assignments_scope check (
        upper(scope_type) in ('SYSTEM', 'TENANT', 'CENTER', 'SCHOOL', 'CLASS')
    ),
    constraint ck_user_role_assignments_scope_id check (
        upper(scope_type) in ('SYSTEM', 'TENANT') or scope_id is not null
    ),
    constraint ck_user_role_assignments_expiry check (expires_at is null or expires_at > created_at)
);

create unique index if not exists uq_user_role_assignments_active_scope
    on user_role_assignments (user_id, role_id, tenant_id, scope_type, coalesce(scope_id, ''))
    where revoked_at is null;

create index if not exists idx_user_role_assignments_effective
    on user_role_assignments (tenant_id, user_id, scope_type, scope_id, expires_at)
    where revoked_at is null;

create index if not exists idx_user_role_assignments_role_active
    on user_role_assignments (tenant_id, role_id, user_id)
    where revoked_at is null;

-- V6 seeded this stable role id under the legacy dotted name `center.manager`.
-- Canonicalize that known legacy row in place so existing user_roles and
-- role_permissions relationships keep pointing at the same role.
update roles
set name = 'center_manager',
    description = 'Education center manager role',
    updated_at = now()
where id = 'role_center_manager'
  and lower(name) = 'center.manager'
  and not exists (
      select 1 from roles canonical
      where lower(canonical.name) = 'center_manager'
        and canonical.id <> 'role_center_manager'
  );

insert into roles (id, created_at, updated_at, name, description, is_default)
select 'role_it_operator', now(), now(), 'it_operator', 'IT operations role', false
where not exists (select 1 from roles where id = 'role_it_operator' or lower(name) = 'it_operator');

insert into roles (id, created_at, updated_at, name, description, is_default)
select 'role_pdt_operator', now(), now(), 'pdt_operator', 'Academic operations role', false
where not exists (select 1 from roles where id = 'role_pdt_operator' or lower(name) = 'pdt_operator');

insert into roles (id, created_at, updated_at, name, description, is_default)
select 'role_center_manager', now(), now(), 'center_manager', 'Education center manager role', false
where not exists (select 1 from roles where id = 'role_center_manager' or lower(name) = 'center_manager');

insert into roles (id, created_at, updated_at, name, description, is_default)
select 'role_system_admin', now(), now(), 'system.admin', 'System administrator role', false
where not exists (select 1 from roles where id = 'role_system_admin' or lower(name) = 'system.admin');

insert into roles (id, created_at, updated_at, name, description, is_default)
select 'role_god_mode', now(), now(), 'system.god_mode', 'Reserved break-glass role; disabled for ordinary assignment', false
where not exists (select 1 from roles where id = 'role_god_mode' or lower(name) = 'system.god_mode');
