ALTER TABLE crm_school_profiles
    ADD COLUMN IF NOT EXISTS lead_status varchar(32) NOT NULL DEFAULT 'new_lead',
    ADD COLUMN IF NOT EXISTS segment varchar(255),
    ADD COLUMN IF NOT EXISTS probability integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS expected_students integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS expected_classes integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_follow_up_at date,
    ADD COLUMN IF NOT EXISTS last_touch_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS risks jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

ALTER TABLE crm_school_deals
    ADD COLUMN IF NOT EXISTS code varchar(100),
    ADD COLUMN IF NOT EXISTS academic_year varchar(32),
    ADD COLUMN IF NOT EXISTS contract_term varchar(100),
    ADD COLUMN IF NOT EXISTS start_month varchar(32),
    ADD COLUMN IF NOT EXISTS revenue_lines jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS cost_lines jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS idempotency_key varchar(192),
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

ALTER TABLE crm_school_activities
    ADD COLUMN IF NOT EXISTS title varchar(500),
    ADD COLUMN IF NOT EXISTS due_at date,
    ADD COLUMN IF NOT EXISTS done boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS idempotency_key varchar(192),
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS crm_handovers (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(36) NOT NULL,
    school_id varchar(36) NOT NULL,
    lead_id varchar(36) NOT NULL,
    opportunity_id varchar(36) NOT NULL,
    requested_by varchar(36) NOT NULL,
    status varchar(32) NOT NULL,
    checklist jsonb NOT NULL DEFAULT '[]'::jsonb,
    idempotency_key varchar(192),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_crm_handovers_school FOREIGN KEY (school_id) REFERENCES education_units(id),
    CONSTRAINT fk_crm_handovers_lead FOREIGN KEY (lead_id) REFERENCES crm_school_profiles(id),
    CONSTRAINT fk_crm_handovers_opportunity FOREIGN KEY (opportunity_id) REFERENCES crm_school_deals(id)
);

CREATE INDEX IF NOT EXISTS idx_crm_profiles_lead_status ON crm_school_profiles(lead_status);
CREATE INDEX IF NOT EXISTS idx_crm_activities_due ON crm_school_activities(due_at, done);
CREATE INDEX IF NOT EXISTS idx_crm_handovers_tenant_school ON crm_handovers(tenant_id, school_id);
CREATE INDEX IF NOT EXISTS idx_crm_handovers_opportunity ON crm_handovers(opportunity_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_crm_deals_owner_idempotency
    ON crm_school_deals(owner_user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_crm_activities_actor_idempotency
    ON crm_school_activities(actor_user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_crm_handovers_tenant_actor_idempotency
    ON crm_handovers(tenant_id, requested_by, idempotency_key) WHERE idempotency_key IS NOT NULL;

ALTER TABLE crm_handovers ENABLE ROW LEVEL SECURITY;
CREATE POLICY crm_handovers_tenant_isolation ON crm_handovers
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
