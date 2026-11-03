-- Preflight for V88__lms_operational_write_contract.sql (read-only).
--
-- V88 creates three UNIQUE indexes without deduplicating first. On a database whose Flyway
-- version is below 88, any duplicate below makes V88 fail ("could not create unique index"),
-- Flyway rolls the migration back and the application does not start.
--
-- Run against the TARGET database before deploying:
--   psql "$TARGET_URL" -v ON_ERROR_STOP=1 -f ops/postgres/preflight-v88-duplicates.sql
-- Every *_duplicate_keys value must be 0. Otherwise follow docs/runbooks/postgres-migration-preflight.md.
-- Output is counts only, so it is safe to paste into a ticket (no names or codes are printed).

SELECT coalesce(max(version::int), 0) AS applied_flyway_version
FROM flyway_schema_history
WHERE success AND version ~ '^[0-9]+$';

-- uq_lms_students_tenant_code: (tenant_id, lower(student_code))
SELECT count(*)                  AS lms_students_duplicate_keys,
       coalesce(sum(rows), 0)    AS lms_students_rows_in_duplicate_keys
FROM (
    SELECT count(*) AS rows
    FROM lms_students
    GROUP BY tenant_id, lower(student_code)
    HAVING count(*) > 1
) duplicates;

-- uq_lms_subjects_tenant_code: (tenant_id, lower(code))
SELECT count(*)                  AS lms_subjects_duplicate_keys,
       coalesce(sum(rows), 0)    AS lms_subjects_rows_in_duplicate_keys
FROM (
    SELECT count(*) AS rows
    FROM lms_subjects
    GROUP BY tenant_id, lower(code)
    HAVING count(*) > 1
) duplicates;

-- uq_academic_classes_tenant_school_year_name: (tenant_id, school_id, lower(name), coalesce(academic_year, ''))
SELECT count(*)                  AS academic_classes_duplicate_keys,
       coalesce(sum(rows), 0)    AS academic_classes_rows_in_duplicate_keys
FROM (
    SELECT count(*) AS rows
    FROM academic_classes
    GROUP BY tenant_id, school_id, lower(name), coalesce(academic_year, '')
    HAVING count(*) > 1
) duplicates;
