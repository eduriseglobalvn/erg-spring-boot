-- Read-only inventory for V160 (role consolidation, spec section 4.7 step 1).
-- Run on EACH environment (local, then server, only with the owner's approval) before migrating:
--   psql -U erg -d erg -f docs/runbooks/role-consolidation-inventory.sql
-- Nothing here writes. Compare the output with the mapping table in V160.

-- 1. Per role: rows in user_roles, active / revoked assignments, access-scope rows (stored as id or name).
select r.id, r.name,
       (select count(*) from user_roles ur where ur.role_id = r.id)                                        as user_roles,
       (select count(*) from user_role_assignments a where a.role_id = r.id and a.revoked_at is null)      as assignments_active,
       (select count(*) from user_role_assignments a where a.role_id = r.id and a.revoked_at is not null)  as assignments_revoked,
       (select count(*) from user_access_scopes s where s.role in (r.id, r.name))                          as access_scopes
from roles r
order by r.id;

-- 2. Distinct users per role (union of both tables, active assignments only).
select r.id as role_id, count(distinct u.user_id) as users
from roles r
left join (select user_id, role_id from user_roles
           union select user_id, role_id from user_role_assignments where revoked_at is null) u on u.role_id = r.id
group by r.id order by r.id;

-- 3. Users holding more than one distinct role (they may collapse into one new role, or break the exclusive-role rule).
select user_id, string_agg(distinct role_id, ', ' order by role_id) as roles
from (select user_id, role_id from user_roles
      union select user_id, role_id from user_role_assignments where revoked_at is null) x
group by user_id having count(distinct role_id) > 1
order by user_id;

-- 4. Roles outside the 7 target roles that still have people (these are the ones V160 maps).
select r.id, r.name, count(distinct u.user_id) as users
from roles r
join (select user_id, role_id from user_roles
      union select user_id, role_id from user_role_assignments) u on u.role_id = r.id
where r.id not in ('role_super_admin','role_director','role_pdt_operator','role_operations','role_lms_teacher','role_lms_student','role_admissions')
group by r.id, r.name order by r.id;

-- 5. Values in user_access_scopes.role (mix of ids and names).
select role, count(*) from user_access_scopes group by role order by role;

-- 6. Permissions V160 removes, plus rows that reference them.
select p.name,
       (select count(*) from role_permissions rp where rp.permission_id = p.id) as role_permissions,
       (select count(*) from permission_bindings b where b.permission_id = p.id) as bindings
from permissions p
where p.name in ('rbac.role.create','rbac.role.update','rbac.role.delete','rbac.*','rbac.override.manage');
select count(*) as user_permissions_rows from user_permissions;

-- 7. Active super admins (must stay >= 1) and users with no role at all.
select count(distinct u.user_id) as super_admins
from (select user_id, role_id from user_roles
      union select user_id, role_id from user_role_assignments where revoked_at is null) u
where u.role_id in ('role_super_admin','role_system_admin','role_lms_admin','role_it_operator');
select id, email from users u
where not exists (select 1 from user_roles r where r.user_id = u.id)
  and not exists (select 1 from user_role_assignments a where a.user_id = u.id and a.revoked_at is null);
