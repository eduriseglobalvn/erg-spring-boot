CREATE TABLE IF NOT EXISTS lms_attendance_records (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    class_id varchar(36) NOT NULL,
    student_id varchar(36) NOT NULL,
    subject_id varchar(100) NOT NULL,
    attendance_date date NOT NULL,
    lesson_index integer NOT NULL,
    status varchar(16) NOT NULL,
    version bigint NOT NULL DEFAULT 1,
    updated_by varchar(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_lms_attendance_lesson CHECK (lesson_index BETWEEN 1 AND 20),
    CONSTRAINT chk_lms_attendance_status CHECK (status IN ('PRESENT','ABSENT','LATE','EXCUSED')),
    CONSTRAINT uq_lms_attendance_natural UNIQUE (tenant_id, class_id, student_id, subject_id, attendance_date, lesson_index)
);

CREATE TABLE IF NOT EXISTS lms_gradebook_columns (
    tenant_id varchar(255) NOT NULL,
    class_id varchar(36) NOT NULL,
    subject_id varchar(100) NOT NULL,
    id varchar(100) NOT NULL,
    label varchar(160) NOT NULL,
    max_score numeric(12,2) NOT NULL,
    kind varchar(16) NOT NULL,
    position integer NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 1,
    updated_by varchar(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    PRIMARY KEY (tenant_id, class_id, subject_id, id),
    CONSTRAINT chk_lms_gradebook_column_max CHECK (max_score > 0),
    CONSTRAINT chk_lms_gradebook_column_kind CHECK (kind IN ('TOPIC','QUIZ','OFFLINE'))
);

CREATE TABLE IF NOT EXISTS lms_gradebook_scores (
    tenant_id varchar(255) NOT NULL,
    class_id varchar(36) NOT NULL,
    subject_id varchar(100) NOT NULL,
    student_id varchar(36) NOT NULL,
    column_id varchar(100) NOT NULL,
    score numeric(12,2) NOT NULL,
    max_score numeric(12,2) NOT NULL,
    version bigint NOT NULL DEFAULT 1,
    updated_by varchar(36) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    PRIMARY KEY (tenant_id, class_id, subject_id, student_id, column_id),
    CONSTRAINT fk_lms_gradebook_score_column FOREIGN KEY (tenant_id, class_id, subject_id, column_id)
      REFERENCES lms_gradebook_columns(tenant_id, class_id, subject_id, id) ON DELETE CASCADE,
    CONSTRAINT chk_lms_gradebook_score_range CHECK (score >= 0 AND max_score > 0 AND score <= max_score)
);

CREATE INDEX IF NOT EXISTS idx_lms_attendance_workspace ON lms_attendance_records(tenant_id, class_id, subject_id, attendance_date, lesson_index);
CREATE INDEX IF NOT EXISTS idx_lms_gradebook_columns_workspace ON lms_gradebook_columns(tenant_id, class_id, subject_id, position);
CREATE INDEX IF NOT EXISTS idx_lms_gradebook_workspace ON lms_gradebook_scores(tenant_id, class_id, subject_id, student_id);

ALTER TABLE lms_attendance_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE lms_gradebook_columns ENABLE ROW LEVEL SECURITY;
ALTER TABLE lms_gradebook_scores ENABLE ROW LEVEL SECURITY;

CREATE POLICY lms_attendance_records_tenant_isolation ON lms_attendance_records
  USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
  WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));
CREATE POLICY lms_gradebook_columns_tenant_isolation ON lms_gradebook_columns
  USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
  WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));
CREATE POLICY lms_gradebook_scores_tenant_isolation ON lms_gradebook_scores
  USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
  WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));

WITH runtime_permissions(name) AS (VALUES
    ('lms.attendance.read'),
    ('lms.attendance.write'),
    ('lms.grade.read'),
    ('lms.grade.write')
)
INSERT INTO permission_bindings (
    id, permission_id, surface_type, surface_identifier, description, enforced, created_at, updated_at
)
SELECT
    'pb_' || substr(md5(permission.name || ':lms-academic-record-graphql'), 1, 32),
    permission.id,
    'GRAPHQL',
    CASE permission.name
      WHEN 'lms.attendance.read' THEN 'LmsQuery.attendanceWorkspace'
      WHEN 'lms.attendance.write' THEN 'Mutation.upsertAttendance'
      WHEN 'lms.grade.read' THEN 'LmsQuery.gradebookWorkspace'
      ELSE 'Mutation.upsertGradebookScore'
    END,
    'Tenant/class-scoped LMS academic record contract',
    true,
    now(),
    now()
FROM permissions permission
JOIN runtime_permissions runtime ON runtime.name = permission.name
ON CONFLICT (permission_id, surface_type, surface_identifier) DO UPDATE
SET enforced = true, description = EXCLUDED.description, updated_at = now();
