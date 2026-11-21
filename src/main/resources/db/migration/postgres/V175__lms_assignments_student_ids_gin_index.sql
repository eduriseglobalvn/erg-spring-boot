-- Student assignment list: the student_ids @> branch had no index. jsonb_path_ops serves the @> operator only.
-- Non-transactional by design: ONLY `CREATE INDEX CONCURRENTLY` (see V114). If interrupted, drop any INVALID index
-- and re-run per docs/runbooks/postgres-migration-preflight.md.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_lms_assignments_student_ids_gin
    ON lms_assignments USING gin (student_ids jsonb_path_ops);
