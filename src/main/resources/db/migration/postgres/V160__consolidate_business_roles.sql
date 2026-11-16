-- V160: consolidate 21 legacy roles into the 7 business roles (spec 2026-11-16-role-consolidation, section 4.7).
-- Inventory taken on LOCAL before writing this file (docs/runbooks/role-consolidation-inventory.sql). The server has NOT been inventoried.
--   role_lms_student 52 users | role_lms_subject_teacher 8 | role_lms_teacher 7 | role_pdt_operator 3 | role_super_admin 1
--   every other legacy role: 0 users. 0 revoked assignments. 0 user_permissions rows.
--   8 users carry two roles (7 teachers hold both teacher roles; 1 holds subject_teacher + pdt_operator) -> hence INSERT-then-DELETE, not UPDATE.
--   user_access_scopes.role mixes ids and names: lms.learner 4, role_lms_subject_teacher 13, role_pdt_operator 4, system.super_admin 1.
-- New roles are created EMPTY: BusinessRoleSynchronizer fills role_permissions on startup.
-- Mapping: teacher roles -> role_lms_teacher; seo.* -> role_admissions; system.admin/lms.admin/it_operator -> role_super_admin;
--          center_manager/school.manager/school.vice_principal/executive.director/lms_teacher_readonly -> role_operations.
-- Owner decision 2026-11-16: an account that would hold Vận hành/Giám đốc (exclusive) together with another role is reset to a
-- blank account (no roles, no scopes, no direct permissions, sessions revoked) and is granted again by hand.
-- system.god_mode holders are reset the same way and get Quản trị only.
set local app.maintenance_mode = 'true';

-- 1. the 7 target roles
insert into roles(id, created_at, updated_at, name, description, is_default) values
  ('role_super_admin',  now(), now(), 'system.super_admin', 'Quản trị hệ thống', false),
  ('role_director',     now(), now(), 'director',           'Giám đốc',          false),
  ('role_pdt_operator', now(), now(), 'pdt_operator',       'Phòng đào tạo',     false),
  ('role_operations',   now(), now(), 'operations',         'Phòng vận hành',    false),
  ('role_lms_teacher',  now(), now(), 'lms.instructor',     'Giáo viên',         false),
  ('role_lms_student',  now(), now(), 'lms.learner',        'Học sinh',          false),
  ('role_admissions',   now(), now(), 'admissions',         'Tuyển sinh (CRM)',  false)
on conflict do nothing;

-- 2. legacy -> new mapping (new_id null = drop)
create temp table role_map(old_id text primary key, new_id text) on commit drop;
insert into role_map values
  ('role_lms_subject_teacher','role_lms_teacher'), ('role_lms_homeroom_teacher','role_lms_teacher'),
  ('role_lms_teacher_assistant','role_lms_teacher'), ('role_lms_teacher_lead','role_lms_teacher'),
  ('role_lms_teacher_readonly','role_operations'), ('role_lms_teacher_standard','role_lms_teacher'),
  ('role_seo_lead','role_admissions'), ('role_seo_manager','role_admissions'), ('role_seo_staff','role_admissions'),
  ('role_system_admin','role_super_admin'), ('role_lms_admin','role_super_admin'), ('role_it_operator','role_super_admin'),
  ('role_center_manager','role_operations'), ('role_school_manager','role_operations'),
  ('role_school_vice_principal','role_operations'), ('role_executive_director','role_operations'),
  ('role_god_mode', null);

-- refuse to continue if some role is neither a target nor mapped (never silently drop someone's access)
do $$
declare unmapped text;
begin
  select string_agg(id, ', ') into unmapped from roles
   where id not in ('role_super_admin','role_director','role_pdt_operator','role_operations','role_lms_teacher','role_lms_student','role_admissions')
     and id not in (select old_id from role_map);
  if unmapped is not null then
    raise exception 'V160: legacy roles without a mapping: %', unmapped;
  end if;
end $$;

-- 2b. accounts to reset (see header). shortcut: users.id is kept, because CRM deals (RESTRICT), classes, schedules and audit
--     rows point at it; a literal row delete would destroy that history. Same email/password, nothing granted.
create temp table reset_users(user_id text primary key, grant_role text) on commit drop;
with held as (
  select ur.user_id, coalesce(m.new_id, ur.role_id) as role_id, ur.role_id as old_id
    from user_roles ur left join role_map m on m.old_id = ur.role_id
  union
  select a.user_id, coalesce(m.new_id, a.role_id), a.role_id
    from user_role_assignments a left join role_map m on m.old_id = a.role_id where a.revoked_at is null
)
insert into reset_users
  select user_id, case when bool_or(old_id = 'role_god_mode') then 'role_super_admin' end
  from held group by user_id
  having bool_or(old_id = 'role_god_mode')
      or (bool_or(role_id in ('role_operations', 'role_director')) and count(distinct role_id) > 1);

do $$
declare ids text;
begin
  select string_agg(user_id || coalesce(' -> ' || grant_role, ''), ', ') into ids from reset_users;
  if ids is not null then raise notice 'V160 reset accounts: %', ids; end if;
end $$;

delete from user_roles where user_id in (select user_id from reset_users);
delete from user_role_assignments where user_id in (select user_id from reset_users);
delete from user_access_scopes where user_id in (select user_id from reset_users);
delete from user_permissions where user_id in (select user_id from reset_users);
update user_sessions set revoked_at = now(), revoked_reason = 'V160 account reset'
 where revoked_at is null and user_id in (select user_id from reset_users);
insert into user_roles(user_id, role_id) select user_id, grant_role from reset_users where grant_role is not null;

-- 3. user_roles
insert into user_roles(user_id, role_id)
  select distinct ur.user_id, m.new_id from user_roles ur join role_map m on m.old_id = ur.role_id where m.new_id is not null
on conflict do nothing;

-- 4. user_role_assignments: active rows first (one per unique scope key), keeping the scope
insert into user_role_assignments(id, user_id, role_id, tenant_id, scope_type, scope_id, expires_at, assigned_by, created_at, updated_at)
  select gen_random_uuid()::text, d.user_id, d.new_id, d.tenant_id, d.scope_type, d.scope_id, d.expires_at, d.assigned_by, d.created_at, now()
  from (
    select distinct on (a.user_id, m.new_id, a.tenant_id, a.scope_type, coalesce(a.scope_id, ''))
           a.user_id, m.new_id, a.tenant_id, a.scope_type, a.scope_id, a.expires_at, a.assigned_by, a.created_at
    from user_role_assignments a join role_map m on m.old_id = a.role_id
    where m.new_id is not null and a.revoked_at is null
    order by a.user_id, m.new_id, a.tenant_id, a.scope_type, coalesce(a.scope_id, ''), a.expires_at nulls first
  ) d
  where not exists (select 1 from user_role_assignments x
                    where x.user_id = d.user_id and x.role_id = d.new_id and x.tenant_id = d.tenant_id
                      and x.scope_type = d.scope_type and coalesce(x.scope_id, '') = coalesce(d.scope_id, '') and x.revoked_at is null);
-- revoked rows keep their history (the partial unique index ignores them)
insert into user_role_assignments(id, user_id, role_id, tenant_id, scope_type, scope_id, expires_at, assigned_by, revoked_at, revoked_by, revoke_reason, created_at, updated_at)
  select gen_random_uuid()::text, a.user_id, m.new_id, a.tenant_id, a.scope_type, a.scope_id, a.expires_at, a.assigned_by, a.revoked_at, a.revoked_by, a.revoke_reason, a.created_at, a.updated_at
  from user_role_assignments a join role_map m on m.old_id = a.role_id
  where m.new_id is not null and a.revoked_at is not null;

-- 5. user_access_scopes.role: ids and names -> new id (a dropped role leaves null)
create temp table role_key_map(k text primary key, new_id text) on commit drop;
insert into role_key_map
  select r.id, m.new_id from roles r join role_map m on m.old_id = r.id
  union select r.name, m.new_id from roles r join role_map m on m.old_id = r.id where r.name is not null
  union select id, id from roles where id in ('role_super_admin','role_director','role_pdt_operator','role_operations','role_lms_teacher','role_lms_student','role_admissions')
  union select name, id from roles where id in ('role_super_admin','role_director','role_pdt_operator','role_operations','role_lms_teacher','role_lms_student','role_admissions');
update user_access_scopes s set role = k.new_id from role_key_map k where s.role = k.k and s.role is distinct from k.new_id;

-- 6. drop removed permissions (rbac role-definition + override); permission_bindings has no cascade
delete from permission_bindings where permission_id in (select id from permissions where name in ('rbac.role.create','rbac.role.update','rbac.role.delete','rbac.*','rbac.override.manage'));
delete from user_permissions where permission in ('rbac.role.create','rbac.role.update','rbac.role.delete','rbac.*','rbac.override.manage');
delete from permissions where name in ('rbac.role.create','rbac.role.update','rbac.role.delete','rbac.*','rbac.override.manage');

-- 7. delete legacy roles (cascades their leftover role_permissions / user_roles / assignments)
delete from roles where id in (select old_id from role_map);

-- 8. invalidate cached principals
update users set authz_version = authz_version + 1;
