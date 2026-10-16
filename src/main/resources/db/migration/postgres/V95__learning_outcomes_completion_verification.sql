-- BE-058 follow-up: durable completion evaluation and certificate verification/revocation.
-- Existing outcome certificates receive a stable internal verification id; no
-- public/anonymous endpoint is introduced by this migration.
ALTER TABLE outcome_certificates
    ADD COLUMN IF NOT EXISTS verification_id varchar(96);

UPDATE outcome_certificates
SET verification_id = id
WHERE verification_id IS NULL;

ALTER TABLE outcome_certificates
    ALTER COLUMN verification_id SET NOT NULL;

ALTER TABLE outcome_certificates
    ADD COLUMN IF NOT EXISTS revoked_by varchar(96),
    ADD COLUMN IF NOT EXISTS revocation_reason varchar(255);

ALTER TABLE competency_attainments
    ADD COLUMN IF NOT EXISTS progress_percent integer NOT NULL DEFAULT 100;
ALTER TABLE competency_attainments
    DROP CONSTRAINT IF EXISTS ck_competency_attainments_status;
ALTER TABLE competency_attainments
    ADD CONSTRAINT ck_competency_attainments_status
        CHECK (status IN ('IN_PROGRESS', 'ATTAINED', 'REVOKED'));
ALTER TABLE competency_attainments
    ADD CONSTRAINT ck_competency_attainments_progress
        CHECK (progress_percent BETWEEN 0 AND 100);

CREATE UNIQUE INDEX IF NOT EXISTS ux_outcome_certificates_verification
    ON outcome_certificates (tenant_id, verification_id);

CREATE TABLE IF NOT EXISTS learning_path_completions (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(96) NOT NULL,
    learner_id varchar(96) NOT NULL,
    learning_path_id varchar(96) NOT NULL,
    status varchar(32) NOT NULL,
    required_node_count integer NOT NULL,
    completed_node_count integer NOT NULL,
    progress_percent integer NOT NULL,
    evidence_ref varchar(255) NOT NULL,
    evaluated_at timestamptz NOT NULL,
    evaluated_by varchar(96) NOT NULL,
    record_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_learning_path_completions_status CHECK (status IN ('INCOMPLETE', 'COMPLETED')),
    CONSTRAINT ck_learning_path_completions_counts CHECK (
        required_node_count >= 0 AND completed_node_count >= 0
    ),
    CONSTRAINT ck_learning_path_completions_progress CHECK (progress_percent BETWEEN 0 AND 100)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_learning_path_completions_identity
    ON learning_path_completions (tenant_id, learner_id, learning_path_id);
CREATE INDEX IF NOT EXISTS idx_learning_path_completions_tenant_learner
    ON learning_path_completions (tenant_id, learner_id, evaluated_at DESC);
CREATE INDEX IF NOT EXISTS idx_learning_path_completions_tenant_path
    ON learning_path_completions (tenant_id, learning_path_id, status);

ALTER TABLE learning_path_completions ENABLE ROW LEVEL SECURITY;
ALTER TABLE learning_path_completions FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS learning_path_completions_tenant_isolation ON learning_path_completions;
CREATE POLICY learning_path_completions_tenant_isolation ON learning_path_completions
    USING (
        current_setting('app.maintenance_mode', true) = 'true'
        OR (nullif(current_setting('app.tenant_id', true), '') IS NOT NULL
            AND tenant_id = current_setting('app.tenant_id', true))
    )
    WITH CHECK (
        current_setting('app.maintenance_mode', true) = 'true'
        OR (nullif(current_setting('app.tenant_id', true), '') IS NOT NULL
            AND tenant_id = current_setting('app.tenant_id', true))
    );
