-- V149__teaching_period_templates.sql
-- Period templates (khung tiet): which clock time each period 1..10 has. Ids are VARCHAR(36) like the rest of the schema.
CREATE TABLE teaching_period_templates (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL,
    name            VARCHAR(80) NOT NULL,
    periods_per_day SMALLINT NOT NULL CHECK (periods_per_day BETWEEN 1 AND 10),
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_tpt_default_per_tenant ON teaching_period_templates (tenant_id) WHERE is_default;

CREATE TABLE teaching_period_template_slots (
    template_id VARCHAR(36) NOT NULL REFERENCES teaching_period_templates (id) ON DELETE CASCADE,
    period_no   SMALLINT NOT NULL CHECK (period_no BETWEEN 1 AND 10),
    session     VARCHAR(10) NOT NULL CHECK (session IN ('MORNING', 'AFTERNOON')),
    start_time  TIME NULL,
    end_time    TIME NULL,
    PRIMARY KEY (template_id, period_no),
    CHECK ((start_time IS NULL) = (end_time IS NULL))
);

CREATE TABLE school_period_templates (
    school_id   VARCHAR(36) PRIMARY KEY,
    template_id VARCHAR(36) NOT NULL REFERENCES teaching_period_templates (id)
);

-- Seed the THCS template (KE_HOACH_LICH_DAY.md 2.1) as the default of every tenant that already has a school.
-- Periods 5 and 10 have no time ("trong gio"). Tenants created later get it lazily from PeriodTemplateService.
INSERT INTO teaching_period_templates (id, tenant_id, name, periods_per_day, is_default)
SELECT gen_random_uuid()::text, t.tenant_id, 'THCS · 10 tiết/ngày', 10, TRUE
FROM (SELECT DISTINCT tenant_id FROM education_units WHERE tenant_id IS NOT NULL) t;

INSERT INTO teaching_period_template_slots (template_id, period_no, session, start_time, end_time)
SELECT tpl.id, s.period_no, s.session, s.start_time, s.end_time
FROM teaching_period_templates tpl
CROSS JOIN (VALUES
    (1, 'MORNING',   TIME '07:15', TIME '08:00'),
    (2, 'MORNING',   TIME '08:00', TIME '08:45'),
    (3, 'MORNING',   TIME '09:15', TIME '10:00'),
    (4, 'MORNING',   TIME '10:00', TIME '10:45'),
    (5, 'MORNING',   NULL::time,   NULL::time),
    (6, 'AFTERNOON', TIME '13:30', TIME '14:15'),
    (7, 'AFTERNOON', TIME '14:15', TIME '15:00'),
    (8, 'AFTERNOON', TIME '15:30', TIME '16:15'),
    (9, 'AFTERNOON', TIME '16:15', TIME '17:00'),
    (10, 'AFTERNOON', NULL::time,  NULL::time)
) AS s (period_no, session, start_time, end_time)
WHERE tpl.is_default;
