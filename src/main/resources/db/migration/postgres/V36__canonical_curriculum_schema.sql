CREATE TABLE IF NOT EXISTS curriculum_subjects (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    code varchar(96) NOT NULL,
    name varchar(255) NOT NULL,
    description text,
    status varchar(32) NOT NULL DEFAULT 'active',
    sort_order integer NOT NULL DEFAULT 0,
    created_by varchar(36),
    updated_by varchar(36),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT uq_curriculum_subjects_tenant_code UNIQUE (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS curriculum_levels (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    subject_id varchar(36) NOT NULL,
    code varchar(96) NOT NULL,
    name varchar(255) NOT NULL,
    description text,
    status varchar(32) NOT NULL DEFAULT 'active',
    sort_order integer NOT NULL DEFAULT 0,
    created_by varchar(36),
    updated_by varchar(36),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT uq_curriculum_levels_tenant_subject_code UNIQUE (tenant_id, subject_id, code),
    CONSTRAINT fk_curriculum_levels_subject
        FOREIGN KEY (subject_id) REFERENCES curriculum_subjects(id)
        ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS curriculum_topics (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    level_id varchar(36) NOT NULL,
    code varchar(96) NOT NULL,
    name varchar(255) NOT NULL,
    description text,
    status varchar(32) NOT NULL DEFAULT 'active',
    sort_order integer NOT NULL DEFAULT 0,
    created_by varchar(36),
    updated_by varchar(36),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT uq_curriculum_topics_tenant_level_code UNIQUE (tenant_id, level_id, code),
    CONSTRAINT fk_curriculum_topics_level
        FOREIGN KEY (level_id) REFERENCES curriculum_levels(id)
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_curriculum_subjects_tenant_status_sort
    ON curriculum_subjects(tenant_id, status, sort_order);
CREATE INDEX IF NOT EXISTS idx_curriculum_subjects_tenant_code
    ON curriculum_subjects(tenant_id, code);
CREATE INDEX IF NOT EXISTS idx_curriculum_subjects_tenant_updated
    ON curriculum_subjects(tenant_id, updated_at);

CREATE INDEX IF NOT EXISTS idx_curriculum_levels_tenant_status_sort
    ON curriculum_levels(tenant_id, status, sort_order);
CREATE INDEX IF NOT EXISTS idx_curriculum_levels_tenant_subject_status
    ON curriculum_levels(tenant_id, subject_id, status);
CREATE INDEX IF NOT EXISTS idx_curriculum_levels_tenant_code
    ON curriculum_levels(tenant_id, code);
CREATE INDEX IF NOT EXISTS idx_curriculum_levels_tenant_updated
    ON curriculum_levels(tenant_id, updated_at);

CREATE INDEX IF NOT EXISTS idx_curriculum_topics_tenant_status_sort
    ON curriculum_topics(tenant_id, status, sort_order);
CREATE INDEX IF NOT EXISTS idx_curriculum_topics_tenant_level_status
    ON curriculum_topics(tenant_id, level_id, status);
CREATE INDEX IF NOT EXISTS idx_curriculum_topics_tenant_code
    ON curriculum_topics(tenant_id, code);
CREATE INDEX IF NOT EXISTS idx_curriculum_topics_tenant_updated
    ON curriculum_topics(tenant_id, updated_at);
