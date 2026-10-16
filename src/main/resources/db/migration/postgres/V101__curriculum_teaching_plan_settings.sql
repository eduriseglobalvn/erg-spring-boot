CREATE TABLE IF NOT EXISTS curriculum_teaching_plan_settings (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    school_id varchar(36) NOT NULL,
    subject_id varchar(36) NOT NULL,
    academic_year varchar(16) NOT NULL,
    start_date date NOT NULL,
    teaching_days varchar(32) NOT NULL DEFAULT '2,4,6',
    sessions_per_day integer NOT NULL DEFAULT 1,
    timezone varchar(64) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    status varchar(32) NOT NULL DEFAULT 'active',
    record_version bigint NOT NULL DEFAULT 0,
    created_by varchar(36),
    updated_by varchar(36),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_curriculum_teaching_plan_scope UNIQUE (tenant_id, school_id, subject_id, academic_year),
    CONSTRAINT chk_curriculum_teaching_plan_sessions CHECK (sessions_per_day BETWEEN 1 AND 8)
);

CREATE INDEX IF NOT EXISTS idx_curriculum_teaching_plan_scope
    ON curriculum_teaching_plan_settings(tenant_id, school_id, subject_id, academic_year);
