-- V20__teaching_schedule_conflicts_audit.sql
-- ============================================================================
-- Teaching Schedule Conflicts Audit Table
-- ============================================================================
CREATE TABLE teaching_schedule_conflicts (
    id                   VARCHAR(36) PRIMARY KEY,
    tenant_id            VARCHAR(36) NOT NULL,
    batch_id             VARCHAR(36),
    event_id             VARCHAR(36),
    conflicting_event_id VARCHAR(36),
    conflict_type        VARCHAR(50) NOT NULL,
    severity             VARCHAR(20) NOT NULL DEFAULT 'ERROR',
    message              TEXT,
    client_row_id        VARCHAR(100),
    resolved             BOOLEAN NOT NULL DEFAULT false,
    resolved_by          VARCHAR(36),
    resolved_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_tsc_severity CHECK (severity IN ('ERROR', 'WARNING')),
    CONSTRAINT chk_tsc_conflict_type CHECK (conflict_type IN ('CLASS_TIME_CONFLICT', 'TEACHER_TIME_CONFLICT', 'ROOM_TIME_CONFLICT'))
);

CREATE INDEX idx_tsc_tenant_batch ON teaching_schedule_conflicts(tenant_id, batch_id);
CREATE INDEX idx_tsc_tenant_event ON teaching_schedule_conflicts(tenant_id, event_id);
CREATE INDEX idx_tsc_tenant_type ON teaching_schedule_conflicts(tenant_id, conflict_type);
CREATE INDEX idx_tsc_tenant_unresolved ON teaching_schedule_conflicts(tenant_id, resolved) WHERE resolved = false;
