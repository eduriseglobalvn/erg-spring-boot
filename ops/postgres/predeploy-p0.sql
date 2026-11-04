-- One-shot predeploy gate for plan items P0-10 and P0-2 (docs/second-brain/review/README.md).
-- Run against the TARGET database before starting the new release:
--   psql "$TARGET_URL" -v ON_ERROR_STOP=1 -f ops/postgres/predeploy-p0.sql
-- It stops with an error (and changes nothing) when V88 would fail on duplicate keys.
-- Otherwise it revokes every session that existed when the leaked dump was taken. Safe to re-run.
-- Output is counts only. Credential rotation (Postgres/Redis passwords) stays a manual step:
-- docs/runbooks/credential-rotation-p0-2.md steps 2-4.

BEGIN;

-- P0-10: V88 creates three UNIQUE indexes without deduplicating first.
DO $$
DECLARE
    students int;
    subjects int;
    classes  int;
BEGIN
    SELECT count(*) INTO students FROM (
        SELECT 1 FROM lms_students GROUP BY tenant_id, lower(student_code) HAVING count(*) > 1) d;
    SELECT count(*) INTO subjects FROM (
        SELECT 1 FROM lms_subjects GROUP BY tenant_id, lower(code) HAVING count(*) > 1) d;
    SELECT count(*) INTO classes FROM (
        SELECT 1 FROM academic_classes
        GROUP BY tenant_id, school_id, lower(name), coalesce(academic_year, '') HAVING count(*) > 1) d;
    RAISE NOTICE 'P0-10 duplicate keys: lms_students=%, lms_subjects=%, academic_classes=%',
        students, subjects, classes;
    IF students + subjects + classes > 0 THEN
        RAISE EXCEPTION 'P0-10: duplicate keys found; clean them per docs/runbooks/postgres-migration-preflight.md before deploying';
    END IF;
END $$;

-- P0-2: sessions that existed when the dump (2026-07-16 14:37:13+07) was taken are treated as leaked.
WITH revoked AS (
    UPDATE user_sessions
       SET revoked_at = CURRENT_TIMESTAMP,
           revoked_reason = 'DUMP_EXPOSURE_P0_2'
     WHERE revoked_at IS NULL
       AND created_at <= TIMESTAMPTZ '2026-07-16 14:37:13+07'
    RETURNING 1)
SELECT count(*) AS p0_2_sessions_revoked FROM revoked;

SELECT count(*) AS p0_2_leaked_sessions_still_active
FROM user_sessions
WHERE revoked_at IS NULL AND created_at <= TIMESTAMPTZ '2026-07-16 14:37:13+07';

COMMIT;
