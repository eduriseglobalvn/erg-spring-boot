CREATE TABLE IF NOT EXISTS curriculum_programs (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    subject_id varchar(36) NOT NULL,
    level_id varchar(36) NOT NULL,
    academic_year varchar(16) NOT NULL,
    name varchar(255) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'draft',
    record_version bigint NOT NULL DEFAULT 0,
    created_by varchar(36),
    updated_by varchar(36),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_curriculum_program_scope UNIQUE (tenant_id, subject_id, level_id, academic_year),
    CONSTRAINT fk_curriculum_program_subject FOREIGN KEY (subject_id) REFERENCES curriculum_subjects(id) ON DELETE RESTRICT,
    CONSTRAINT fk_curriculum_program_level FOREIGN KEY (level_id) REFERENCES curriculum_levels(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS curriculum_program_rows (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    program_id varchar(36) NOT NULL,
    topic varchar(255) NOT NULL,
    period integer NOT NULL,
    title varchar(500) NOT NULL,
    sessions integer NOT NULL DEFAULT 1,
    sort_order integer NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'active',
    record_version bigint NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT fk_curriculum_program_rows_program FOREIGN KEY (program_id) REFERENCES curriculum_programs(id) ON DELETE CASCADE,
    CONSTRAINT uq_curriculum_program_row_period UNIQUE (program_id, period),
    CONSTRAINT chk_curriculum_program_row_period CHECK (period > 0),
    CONSTRAINT chk_curriculum_program_row_sessions CHECK (sessions > 0)
);

CREATE INDEX IF NOT EXISTS idx_curriculum_program_scope
    ON curriculum_programs(tenant_id, subject_id, level_id, academic_year, status);
CREATE INDEX IF NOT EXISTS idx_curriculum_program_rows_order
    ON curriculum_program_rows(tenant_id, program_id, sort_order, period);
