-- School operations foundation. education_units remains the shared CRM/LMS identity.

ALTER TABLE education_units ADD COLUMN IF NOT EXISTS tenant_id varchar(36);
UPDATE education_units SET tenant_id = 'erg' WHERE tenant_id IS NULL OR btrim(tenant_id) = '';
ALTER TABLE education_units ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE education_units ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS uk_education_units_tenant_slug
    ON education_units(tenant_id, lower(slug)) WHERE slug IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_education_units_tenant_type_status_name
    ON education_units(tenant_id, type, status, name);

CREATE TABLE IF NOT EXISTS school_operational_profiles (
    school_id varchar(36) PRIMARY KEY,
    tenant_id varchar(36) NOT NULL,
    school_type varchar(32) NOT NULL,
    address varchar(500),
    district varchar(255),
    principal_name varchar(255),
    contact_phone varchar(50),
    contact_email varchar(255),
    onboarding_status varchar(32) NOT NULL DEFAULT 'DRAFT',
    current_academic_year_id varchar(36),
    version bigint NOT NULL DEFAULT 0,
    created_by varchar(36),
    updated_by varchar(36),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_school_profile_school FOREIGN KEY (school_id)
        REFERENCES education_units(id) ON DELETE RESTRICT,
    CONSTRAINT ck_school_profile_type CHECK (school_type IN
        ('PRIMARY', 'SECONDARY', 'HIGH_SCHOOL', 'UNIVERSITY', 'TRAINING_CENTER')),
    CONSTRAINT ck_school_profile_status CHECK (onboarding_status IN
        ('DRAFT', 'ONBOARDING', 'ACTIVE', 'PAUSED', 'ARCHIVED')),
    CONSTRAINT uk_school_profile_tenant_school UNIQUE (tenant_id, school_id)
);

CREATE TABLE IF NOT EXISTS school_academic_years (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(36) NOT NULL,
    school_id varchar(36) NOT NULL,
    start_year integer NOT NULL,
    end_year integer NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PLANNED',
    starts_on date,
    ends_on date,
    version bigint NOT NULL DEFAULT 0,
    created_by varchar(36),
    updated_by varchar(36),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_school_year_school FOREIGN KEY (school_id)
        REFERENCES education_units(id) ON DELETE RESTRICT,
    CONSTRAINT ck_school_year_range CHECK (end_year = start_year + 1),
    CONSTRAINT ck_school_year_dates CHECK (ends_on IS NULL OR starts_on IS NULL OR ends_on >= starts_on),
    CONSTRAINT ck_school_year_status CHECK (status IN ('PLANNED', 'CURRENT', 'CLOSED', 'ARCHIVED')),
    CONSTRAINT uk_school_year_start UNIQUE (tenant_id, school_id, start_year)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_school_year_current
    ON school_academic_years(tenant_id, school_id) WHERE status = 'CURRENT';
CREATE INDEX IF NOT EXISTS idx_school_year_tenant_school_status
    ON school_academic_years(tenant_id, school_id, status, start_year DESC);

ALTER TABLE school_operational_profiles
    ADD CONSTRAINT fk_school_profile_current_year
    FOREIGN KEY (current_academic_year_id) REFERENCES school_academic_years(id)
    ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS school_grades (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(36) NOT NULL,
    school_id varchar(36) NOT NULL,
    academic_year_id varchar(36) NOT NULL,
    grade_code varchar(32) NOT NULL,
    label varchar(100) NOT NULL,
    sort_order integer NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    source varchar(20) NOT NULL DEFAULT 'DEFAULT',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_school_grade_school FOREIGN KEY (school_id)
        REFERENCES education_units(id) ON DELETE RESTRICT,
    CONSTRAINT fk_school_grade_year FOREIGN KEY (academic_year_id)
        REFERENCES school_academic_years(id) ON DELETE RESTRICT,
    CONSTRAINT ck_school_grade_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_school_grade_source CHECK (source IN ('DEFAULT', 'MANUAL', 'IMPORT')),
    CONSTRAINT uk_school_grade_code UNIQUE (tenant_id, school_id, academic_year_id, grade_code)
);

CREATE INDEX IF NOT EXISTS idx_school_grades_tenant_school_year
    ON school_grades(tenant_id, school_id, academic_year_id, status, sort_order);

INSERT INTO permission_groups (id, created_at, updated_at, name, label, display_order)
SELECT 'pg_lms_school_ops', now(), now(), 'LMS School Operations', 'School operations permissions', 31
WHERE NOT EXISTS (SELECT 1 FROM permission_groups WHERE id = 'pg_lms_school_ops');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT permission.id, now(), now(), permission.name, 'lms', permission.label, permission.description
FROM (VALUES
    ('perm_lms_school_read', 'lms.school.read', 'View schools', 'View school directory and operational workspaces'),
    ('perm_lms_school_create', 'lms.school.create', 'Create schools', 'Create a school and its initial academic structure'),
    ('perm_lms_school_update', 'lms.school.update', 'Update schools', 'Update school profile, academic years, and default grades'),
    ('perm_lms_school_archive', 'lms.school.archive', 'Archive schools', 'Archive and restore schools'),
    ('perm_lms_class_manage', 'lms.class.manage', 'Manage classes', 'Manage grades and classes within an allowed school')
) AS permission(id, name, label, description)
WHERE NOT EXISTS (SELECT 1 FROM permissions existing WHERE existing.name = permission.name);

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role CROSS JOIN permissions permission
WHERE role.name IN ('lms.admin', 'school.manager', 'center.manager')
  AND permission.name IN ('lms.school.read', 'lms.school.create', 'lms.school.update', 'lms.school.archive', 'lms.class.manage')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );
