-- Local/demo fixture: links the existing IC3 curriculum seed to the existing demo school.
-- Safe to replay because the scope is protected by the unique constraint.
INSERT INTO curriculum_teaching_plan_settings (
    id, tenant_id, school_id, subject_id, academic_year, start_date,
    teaching_days, sessions_per_day, timezone, status, record_version,
    created_by, updated_by, created_at, updated_at
)
VALUES (
    'ctp_demo_binh_an_ic3_2026', 'erg', 'sch_binh_an', 'sub_ic3', '2026-2027',
    DATE '2026-05-29', '2,4,6', 2, 'Asia/Ho_Chi_Minh', 'active', 0,
    'seed', 'seed', now(), now()
)
ON CONFLICT (tenant_id, school_id, subject_id, academic_year) DO UPDATE SET
    start_date = EXCLUDED.start_date,
    teaching_days = EXCLUDED.teaching_days,
    sessions_per_day = EXCLUDED.sessions_per_day,
    timezone = EXCLUDED.timezone,
    status = EXCLUDED.status,
    updated_by = 'seed',
    updated_at = now();
