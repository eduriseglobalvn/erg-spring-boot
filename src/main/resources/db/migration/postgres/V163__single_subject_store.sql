-- V163: one subject/level store. Owner decision 17/11/2026: KCT (curriculum_subjects / curriculum_levels) is the only
-- source; lms_subjects, lms_levels and lms_topics go away. Until now the timetable, timekeeping and the class log read the
-- LMS tables, so a subject created in «Nội dung» could not be scheduled, and the school setup screen created a private
-- copy ("Subject - Level", code "<levelCode>-<schoolId>") that pointed back at nothing.
-- Inventory taken on the LOCAL test stack only (17/11): 11 lms_subjects (9 share their id with curriculum_subjects, 2 are
-- school-setup copies), 4 lms_levels (all share their id), 1 lms_topics (seed). The server has NOT been inventoried.
-- Nothing referenced is lost: an id that cannot be mapped to a KCT row is moved into KCT under the same id.
set local app.maintenance_mode = 'true';

-- 1. the class studies a subject at a level (8A studies IC3 at Level 3)
alter table curriculum_class_subject_allocations add column if not exists level_id varchar(36);

-- 2. every LMS subject id still referenced by a row somewhere
create temporary table tmp_used_subject on commit drop as
select subject_id id from teaching_schedule_events where subject_id is not null
union select subject_id from curriculum_class_subject_allocations
union select subject_id from lms_assignments where subject_id is not null
union select subject_id from lms_attendance_records where subject_id is not null
union select subject_id from lms_gradebook_columns where subject_id is not null
union select subject_id from lms_gradebook_scores where subject_id is not null
union select subject_id from lms_lesson_log_entries where subject_id is not null
union select subject_id from tk_teaching_month_lines where subject_id is not null
union select subject_id from curriculum_teaching_plan_settings where subject_id is not null
union select subject_id from lms_levels where subject_id is not null;

-- 3. old subject id -> KCT subject id (and the KCT level, for a school-setup copy)
create temporary table tmp_subject_map on commit drop as
select s.id old_id,
       case when same.id is not null then same.id
            when lv.id is not null then lv.subject_id
            else by_code.id end new_id,
       case when same.id is null then lv.id end level_id
from lms_subjects s
left join curriculum_subjects same on same.id = s.id
left join lateral (
    -- a school-setup copy: its code is "<level code>-<school id>"; the longest matching level code wins
    select l.id, l.subject_id from curriculum_levels l
    where l.tenant_id = s.tenant_id
      and left(lower(s.code), length(l.code) + 1) = lower(l.code) || '-'
    order by length(l.code) desc, l.id limit 1) lv on true
left join lateral (
    select c.id from curriculum_subjects c
    where c.tenant_id = s.tenant_id and lower(c.code) = lower(s.code)
    order by c.id limit 1) by_code on true;

-- 3b. a referenced subject with no KCT counterpart moves into KCT under its own id
insert into curriculum_subjects (id, tenant_id, code, name, status, sort_order, lifecycle_state, record_version, created_at, updated_at)
select s.id, s.tenant_id, left(coalesce(nullif(s.code, ''), s.id), 96), coalesce(nullif(s.name, ''), nullif(s.code, ''), s.id),
       lower(coalesce(nullif(s.status, ''), 'active')), 0, 'PUBLISHED', 0, s.created_at, s.updated_at
from lms_subjects s
join tmp_subject_map m on m.old_id = s.id and m.new_id is null
where s.tenant_id is not null and s.id in (select id from tmp_used_subject);

update tmp_subject_map m set new_id = m.old_id
where m.new_id is null and exists (select 1 from curriculum_subjects c where c.id = m.old_id);

do $$
declare lost text;
begin
    select string_agg(u.id, ', ') into lost from tmp_used_subject u
    where not exists (select 1 from tmp_subject_map m where m.old_id = u.id and m.new_id is not null)
      and not exists (select 1 from curriculum_subjects c where c.id = u.id);
    if lost is not null then
        raise exception 'V163: subject ids in use that are in neither store: %', lost;
    end if;
end $$;

-- 4. old level id -> KCT level id
create temporary table tmp_level_map on commit drop as
select l.id old_id, coalesce(same.id, by_code.id) new_id
from lms_levels l
left join curriculum_levels same on same.id = l.id
left join tmp_subject_map sm on sm.old_id = l.subject_id
left join lateral (
    select c.id from curriculum_levels c
    where c.tenant_id = l.tenant_id and c.subject_id = sm.new_id and lower(c.code) = lower(l.code)
    order by c.id limit 1) by_code on true;

insert into curriculum_levels (id, tenant_id, subject_id, code, name, status, sort_order, lifecycle_state, record_version, created_at, updated_at)
select l.id, l.tenant_id, sm.new_id, left(coalesce(nullif(l.code, ''), l.id), 96), coalesce(nullif(l.name, ''), nullif(l.code, ''), l.id),
       lower(coalesce(nullif(l.status, ''), 'active')), coalesce(l.sort_order, 0), 'PUBLISHED', 0, l.created_at, l.updated_at
from lms_levels l
join tmp_level_map m on m.old_id = l.id and m.new_id is null
join tmp_subject_map sm on sm.old_id = l.subject_id and sm.new_id is not null
where l.tenant_id is not null
  and l.id in (select level_id from teaching_schedule_events where level_id is not null);

update tmp_level_map m set new_id = m.old_id
where m.new_id is null and exists (select 1 from curriculum_levels c where c.id = m.old_id);

do $$
declare lost text;
begin
    select string_agg(distinct e.level_id, ', ') into lost from teaching_schedule_events e
    where e.level_id is not null
      and not exists (select 1 from curriculum_levels c where c.id = e.level_id)
      and not exists (select 1 from tmp_level_map m where m.old_id = e.level_id and m.new_id is not null);
    if lost is not null then
        raise exception 'V163: timetable level ids that are in neither store: %', lost;
    end if;
end $$;

-- 5. the old foreign keys go first so ids can be rewritten
do $$
declare fk record;
begin
    for fk in select conrelid::regclass tbl, conname from pg_constraint
              where contype = 'f' and confrelid in ('lms_subjects'::regclass, 'lms_levels'::regclass, 'lms_topics'::regclass)
    loop
        execute format('alter table %s drop constraint %I', fk.tbl, fk.conname);
    end loop;
end $$;

-- 6. rewrite ids that changed
delete from tmp_subject_map where new_id is null or new_id = old_id and level_id is null;
delete from tmp_level_map where new_id is null or new_id = old_id;

-- 6a. class allocations: keep one row per (class, KCT subject), then rewrite
delete from curriculum_class_subject_allocations a
using (
    select a2.id, row_number() over (
               partition by a2.tenant_id, a2.school_id, a2.academic_year, a2.academic_class_id, coalesce(m.new_id, a2.subject_id)
               order by (m.old_id is not null), a2.created_at, a2.id) rn
    from curriculum_class_subject_allocations a2
    left join tmp_subject_map m on m.old_id = a2.subject_id) dup
where dup.id = a.id and dup.rn > 1;

update curriculum_class_subject_allocations a
set subject_id = m.new_id, level_id = coalesce(a.level_id, m.level_id)
from tmp_subject_map m where m.old_id = a.subject_id;

-- 6b. teaching plan settings: an existing KCT-keyed row wins
delete from curriculum_teaching_plan_settings s
using tmp_subject_map m
where m.old_id = s.subject_id and m.new_id <> m.old_id and exists (
    select 1 from curriculum_teaching_plan_settings k
    where k.tenant_id = s.tenant_id and k.school_id = s.school_id and k.academic_year = s.academic_year and k.subject_id = m.new_id);
update curriculum_teaching_plan_settings s set subject_id = m.new_id from tmp_subject_map m where m.old_id = s.subject_id;

-- 6c. monthly teaching-hour lines are sums: two lines that become the same subject cannot be merged blindly
do $$
begin
    if exists (
        select 1 from tk_teaching_month_lines a
        join tmp_subject_map m on m.old_id = a.subject_id and m.new_id <> m.old_id
        join tk_teaching_month_lines b on b.tenant_id = a.tenant_id and b.month = a.month
             and b.teacher_user_id = a.teacher_user_id and b.role = a.role and b.school_id = a.school_id
             and b.id <> a.id and coalesce((select m2.new_id from tmp_subject_map m2 where m2.old_id = b.subject_id), b.subject_id) = m.new_id) then
        raise exception 'V163: tk_teaching_month_lines has lines of two subjects that map to the same KCT subject; merge them by hand first';
    end if;
end $$;
update tk_teaching_month_lines t set subject_id = m.new_id from tmp_subject_map m where m.old_id = t.subject_id;

-- 6d. the rest carry no uniqueness on the subject
update teaching_schedule_events e set subject_id = m.new_id, level_id = coalesce(e.level_id, m.level_id)
from tmp_subject_map m where m.old_id = e.subject_id;
update teaching_schedule_events e set level_id = m.new_id from tmp_level_map m where m.old_id = e.level_id;
update lms_assignments t set subject_id = m.new_id from tmp_subject_map m where m.old_id = t.subject_id;
update lms_attendance_records t set subject_id = m.new_id from tmp_subject_map m where m.old_id = t.subject_id;
update lms_gradebook_columns t set subject_id = m.new_id from tmp_subject_map m where m.old_id = t.subject_id;
update lms_gradebook_scores t set subject_id = m.new_id from tmp_subject_map m where m.old_id = t.subject_id;
update lms_lesson_log_entries t set subject_id = m.new_id from tmp_subject_map m where m.old_id = t.subject_id;

-- 7. the new foreign keys: a KCT subject or level in use cannot be deleted
alter table teaching_schedule_events
    add constraint fk_tse_subject foreign key (subject_id) references curriculum_subjects(id) on delete restrict,
    add constraint fk_tse_level foreign key (level_id) references curriculum_levels(id) on delete restrict;
alter table curriculum_class_subject_allocations
    add constraint fk_class_subject_allocation_subject foreign key (subject_id) references curriculum_subjects(id) on delete restrict,
    add constraint fk_class_subject_allocation_level foreign key (level_id) references curriculum_levels(id) on delete restrict;
alter table lms_assignments
    add constraint fk_lms_assignments_subject foreign key (subject_id) references curriculum_subjects(id) on delete restrict;

-- 8. the second store is gone
drop table lms_topics;
drop table lms_levels;
drop table lms_subjects;
