-- Deterministic read workload for local pg_stat_statements evidence.
-- Run only against the approved seed fixture.
select pg_stat_statements_reset();

do $$
begin
  for i in 1..1000 loop
    perform a.id, a.title, a.status, a.due_at
    from lms_assignments a
    join assignment_recipients r on r.assignment_id = a.id and r.tenant_id = a.tenant_id
    where a.tenant_id = 'seed-tenant-01' and r.student_user_id = 'seed-student-00001'
    order by a.due_at, a.id limit 50;

    perform id, stem, type, difficulty, status, updated_at
    from lms_questions
    where tenant_id = 'seed-tenant-01' and lower(status) = 'active' and deleted_at is null
    order by updated_at desc, id limit 50;

    perform id, academic_class_id, subject_id, start_at, end_at, status
    from teaching_schedule_events
    where tenant_id = 'seed-tenant-01'
      and start_at >= now() and start_at < now() + interval '31 days'
    order by start_at, id limit 500;

    perform id, action, user_id, created_at
    from user_activities
    where tenant_id = 'seed-tenant-01'
    order by created_at desc, id desc limit 50;

    perform id, title, status, visibility, updated_at
    from content_items
    where tenant_id = 'seed-tenant-01' and status = 'published'
    order by updated_at desc nulls last, id limit 50;

    perform count(*), count(*) filter (where status = 'SUBMITTED'), avg(percent)
    from lms_attempts
    where tenant_id = 'seed-tenant-01' and assignment_id = 'seed-assignment-0001-01';
  end loop;
end $$;
