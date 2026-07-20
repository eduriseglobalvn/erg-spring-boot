-- V17__teaching_schedule_schema.sql
-- ============================================================================
-- 1. Teaching Schedule Batches
-- ============================================================================
CREATE TABLE teaching_schedule_batches (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL,
    school_id       VARCHAR(36) NOT NULL,
    academic_year   VARCHAR(20),
    apply_from      DATE NOT NULL,
    repeat_weeks    INT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    source          VARCHAR(30) NOT NULL DEFAULT 'LCMS_BULK_GRID',
    idempotency_key VARCHAR(255),
    payload_hash    VARCHAR(64),
    note            TEXT,
    created_by      VARCHAR(36),
    published_by    VARCHAR(36),
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_tsb_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    CONSTRAINT chk_tsb_repeat_weeks CHECK (repeat_weeks BETWEEN 1 AND 52)
);

CREATE INDEX idx_tsb_tenant_school ON teaching_schedule_batches(tenant_id, school_id);
CREATE INDEX idx_tsb_tenant_status ON teaching_schedule_batches(tenant_id, status);
CREATE UNIQUE INDEX idx_tsb_tenant_idempotency_key
    ON teaching_schedule_batches(tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- ============================================================================
-- 2. Teaching Schedule Events
-- ============================================================================
CREATE TABLE teaching_schedule_events (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    batch_id            VARCHAR(36) NOT NULL,
    school_id            VARCHAR(36) NOT NULL,
    academic_class_id   VARCHAR(36) NOT NULL,
    subject_id          VARCHAR(36) NOT NULL,
    level_id            VARCHAR(36),
    room_id             VARCHAR(36),
    room_name           VARCHAR(255),
    title               VARCHAR(500),
    start_at            TIMESTAMPTZ NOT NULL,
    end_at              TIMESTAMPTZ NOT NULL,
    weekday             SMALLINT NOT NULL,
    session             VARCHAR(20),
    period_start        SMALLINT NOT NULL DEFAULT 1,
    period_count        SMALLINT NOT NULL DEFAULT 1,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    note                TEXT,
    created_by          VARCHAR(36),
    updated_by          VARCHAR(36),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_tse_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    CONSTRAINT chk_tse_weekday CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT chk_tse_period_start CHECK (period_start >= 1),
    CONSTRAINT chk_tse_period_count CHECK (period_count BETWEEN 1 AND 10),
    CONSTRAINT chk_tse_time_range CHECK (start_at < end_at)
);

CREATE INDEX idx_tse_tenant_timerange ON teaching_schedule_events(tenant_id, start_at, end_at);
CREATE INDEX idx_tse_tenant_school_timerange ON teaching_schedule_events(tenant_id, school_id, start_at, end_at);
CREATE INDEX idx_tse_tenant_class_timerange ON teaching_schedule_events(tenant_id, academic_class_id, start_at, end_at);
CREATE INDEX idx_tse_tenant_subject_timerange ON teaching_schedule_events(tenant_id, subject_id, start_at, end_at);
CREATE INDEX idx_tse_batch ON teaching_schedule_events(batch_id);
CREATE INDEX idx_tse_tenant_status ON teaching_schedule_events(tenant_id, status);
CREATE INDEX idx_tse_school_room ON teaching_schedule_events(school_id, room_name) WHERE room_name IS NOT NULL;

-- ============================================================================
-- 3. Teaching Schedule Event Teachers
-- ============================================================================
CREATE TABLE teaching_schedule_event_teachers (
    id                      VARCHAR(36) PRIMARY KEY,
    tenant_id               VARCHAR(36) NOT NULL,
    event_id                VARCHAR(36) NOT NULL,
    teacher_user_id         VARCHAR(36) NOT NULL,
    role                    VARCHAR(20) NOT NULL,
    display_name_snapshot   VARCHAR(255),
    color_key               VARCHAR(100),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_tset_role CHECK (role IN ('MAIN', 'ASSISTANT')),
    CONSTRAINT uq_tset_event_teacher_role UNIQUE (event_id, teacher_user_id, role)
);

CREATE INDEX idx_tset_teacher ON teaching_schedule_event_teachers(tenant_id, teacher_user_id);
CREATE INDEX idx_tset_event ON teaching_schedule_event_teachers(event_id);

-- ============================================================================
-- 4. Teaching Schedule Rooms
-- ============================================================================
CREATE TABLE teaching_schedule_rooms (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL,
    school_id       VARCHAR(36) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(36),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_tsr_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT uq_tsr_school_name UNIQUE (school_id, name)
);

CREATE INDEX idx_tsr_tenant_school ON teaching_schedule_rooms(tenant_id, school_id);
CREATE INDEX idx_tsr_tenant_school_status ON teaching_schedule_rooms(tenant_id, school_id, status);
CREATE INDEX idx_tsr_created_by ON teaching_schedule_rooms(created_by);

-- ============================================================================
-- 5. FK Column Indexes
-- ============================================================================
CREATE INDEX idx_tsb_school ON teaching_schedule_batches(school_id);
CREATE INDEX idx_tsb_created_by ON teaching_schedule_batches(created_by);
CREATE INDEX idx_tsb_published_by ON teaching_schedule_batches(published_by);

CREATE INDEX idx_tse_school ON teaching_schedule_events(school_id);
CREATE INDEX idx_tse_created_by ON teaching_schedule_events(created_by);
CREATE INDEX idx_tse_updated_by ON teaching_schedule_events(updated_by);

CREATE INDEX idx_tset_teacher_user ON teaching_schedule_event_teachers(teacher_user_id);

-- ============================================================================
-- 6. Foreign Keys (NOT VALID)
-- ============================================================================
-- teaching_schedule_batches FKs
ALTER TABLE teaching_schedule_batches
    ADD CONSTRAINT fk_tsb_school
        FOREIGN KEY (school_id) REFERENCES education_units(id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_tsb_created_by
        FOREIGN KEY (created_by) REFERENCES users(id)
        ON DELETE SET NULL NOT VALID,
    ADD CONSTRAINT fk_tsb_published_by
        FOREIGN KEY (published_by) REFERENCES users(id)
        ON DELETE SET NULL NOT VALID;

-- teaching_schedule_events FKs
ALTER TABLE teaching_schedule_events
    ADD CONSTRAINT fk_tse_batch
        FOREIGN KEY (batch_id) REFERENCES teaching_schedule_batches(id)
        ON DELETE CASCADE NOT VALID,
    ADD CONSTRAINT fk_tse_school
        FOREIGN KEY (school_id) REFERENCES education_units(id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_tse_class
        FOREIGN KEY (academic_class_id) REFERENCES academic_classes(id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_tse_subject
        FOREIGN KEY (subject_id) REFERENCES lms_subjects(id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_tse_level
        FOREIGN KEY (level_id) REFERENCES lms_levels(id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_tse_created_by
        FOREIGN KEY (created_by) REFERENCES users(id)
        ON DELETE SET NULL NOT VALID,
    ADD CONSTRAINT fk_tse_updated_by
        FOREIGN KEY (updated_by) REFERENCES users(id)
        ON DELETE SET NULL NOT VALID;

-- teaching_schedule_event_teachers FKs
ALTER TABLE teaching_schedule_event_teachers
    ADD CONSTRAINT fk_tset_event
        FOREIGN KEY (event_id) REFERENCES teaching_schedule_events(id)
        ON DELETE CASCADE NOT VALID,
    ADD CONSTRAINT fk_tset_teacher_user
        FOREIGN KEY (teacher_user_id) REFERENCES users(id)
        ON DELETE CASCADE NOT VALID;

-- teaching_schedule_rooms FKs
ALTER TABLE teaching_schedule_rooms
    ADD CONSTRAINT fk_tsr_school
        FOREIGN KEY (school_id) REFERENCES education_units(id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_tsr_created_by
        FOREIGN KEY (created_by) REFERENCES users(id)
        ON DELETE SET NULL NOT VALID;

-- ============================================================================
-- 7. Seed Schedule Permissions
-- ============================================================================
INSERT INTO permission_groups (id, created_at, updated_at, name, label, display_order)
SELECT 'pg_lms_schedule', now(), now(), 'LMS Schedule', 'Teaching schedule permissions', 30
WHERE NOT EXISTS (SELECT 1 FROM permission_groups WHERE id = 'pg_lms_schedule');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'perm_lms_schedule_read_self', now(), now(), 'lms.schedule.read_self', 'lms',
       'View own schedule',
       'View teaching schedule assigned to the current user'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'lms.schedule.read_self');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'perm_lms_schedule_read', now(), now(), 'lms.schedule.read', 'lms',
       'View aggregate schedule',
       'View teaching schedule across managed education units'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'lms.schedule.read');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'perm_lms_schedule_manage', now(), now(), 'lms.schedule.manage', 'lms',
       'Manage teaching schedule',
       'Create, update, delete, and publish teaching schedules'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'lms.schedule.manage');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'lms.admin'
  AND p.name IN ('lms.schedule.read', 'lms.schedule.manage')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'lms.instructor'
  AND p.name = 'lms.schedule.read_self'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'lms.learner'
  AND p.name = 'lms.schedule.read_self'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
