-- Representative local 5k-fixture query shapes. Save JSON with settings and workload manifest.
explain (analyze, buffers, format json)
select id, title, status, visibility, updated_at
from content_items
where tenant_id = 'seed-tenant-01'
  and status = 'published'
order by updated_at desc nulls last, id
limit 50;

explain (analyze, buffers, format json)
select id, action, user_id, created_at
from user_activities
where tenant_id = 'seed-tenant-01'
order by created_at desc, id desc
limit 50;

explain (analyze, buffers, format json)
select id, academic_class_id, subject_id, start_at, end_at, status
from teaching_schedule_events
where tenant_id = 'seed-tenant-01'
  and start_at >= now()
  and start_at < now() + interval '31 days'
order by start_at, id
limit 500;

explain (analyze, buffers, format json)
select count(*), count(*) filter (where status = 'SUBMITTED'), avg(percent)
from lms_attempts
where tenant_id = 'seed-tenant-01'
  and assignment_id = 'seed-assignment-0001-01';
