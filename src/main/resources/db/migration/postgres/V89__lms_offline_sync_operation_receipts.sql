-- Durable replay receipts for offline-capable LMS mutations.
-- Only a canonical payload hash and the first result are retained; bearer material/PII is excluded.
ALTER TABLE lms_assignments ADD COLUMN IF NOT EXISTS record_version bigint NOT NULL DEFAULT 0;
ALTER TABLE content_items ADD COLUMN IF NOT EXISTS record_version bigint NOT NULL DEFAULT 0;
ALTER TABLE content_assets ADD COLUMN IF NOT EXISTS record_version bigint NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS lms_sync_operations (
    tenant_id varchar(255) NOT NULL,
    account_id varchar(255) NOT NULL,
    operation_id varchar(128) NOT NULL,
    idempotency_key varchar(255) NOT NULL,
    entity_type varchar(160) NOT NULL,
    entity_id varchar(160) NOT NULL,
    payload_hash char(64) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    server_version bigint,
    result_json jsonb,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    PRIMARY KEY (tenant_id, account_id, idempotency_key),
    CONSTRAINT uq_lms_sync_operation_id UNIQUE (tenant_id, account_id, operation_id),
    CONSTRAINT chk_lms_sync_operation_status CHECK (status IN ('PENDING', 'SUCCEEDED'))
);

CREATE INDEX IF NOT EXISTS idx_lms_sync_operations_retention
    ON lms_sync_operations (tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_lms_sync_operations_entity
    ON lms_sync_operations (tenant_id, entity_type, entity_id, updated_at);

ALTER TABLE lms_sync_operations ENABLE ROW LEVEL SECURITY;
CREATE POLICY lms_sync_operations_tenant_isolation ON lms_sync_operations
  USING (current_setting('app.maintenance_mode', true) = 'true'
      OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
  WITH CHECK (current_setting('app.maintenance_mode', true) = 'true'
      OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));

-- Retention is intentionally bounded by a scheduled SQL/job policy rather than deleting receipts
-- inline with a mutation. Production should retain the latest 30 days (or the approved offline TTL).
