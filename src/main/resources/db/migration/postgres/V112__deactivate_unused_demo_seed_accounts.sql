-- D3/N8: demo/seed accounts created by V11 and V21 run in every environment, are ACTIVE and have
-- no password. Deactivate them where they were never used. Accounts are matched by their exact seed
-- ids only (never by pattern); any seed account someone has since given a password or logged in
-- with is left untouched. usr_super_admin is deliberately not in this list.
UPDATE users
SET status = 'INACTIVE',
    updated_at = CURRENT_TIMESTAMP
WHERE id IN (
        -- V11__relationship_integrity_seed_cleanup.sql
        'usr_teacher_ba',
        'usr_teacher_cl',
        'usr_stu_ba_0001',
        'usr_stu_cl_0001',
        'usr_relationship_placeholder',
        -- V21__teaching_calendar_seed_data.sql
        'usr_teacher_truc',
        'usr_teacher_vuong',
        'usr_teacher_tuan',
        'usr_teacher_vang',
        'usr_teacher_tram'
    )
  AND status = 'ACTIVE'
  AND password_hash IS NULL
  AND coalesce(login_count, 0) = 0;
