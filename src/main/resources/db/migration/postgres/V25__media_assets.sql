-- V25__media_assets.sql
-- Phase 1.1 of the Quiz backend (docs/0706/quiz-backend-plan.md §1.6, §5).
-- Hash-first, deduplicated media assets that replace the base64 data URLs the FE editor currently
-- inlines. Slides reference media_assets by id/url instead of embedding bytes. media_references
-- drives ref-counting and orphan GC. All ids are varchar(36) to match the repo id convention.

CREATE TABLE IF NOT EXISTS media_assets (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255) NOT NULL,
    sha256 varchar(64) NOT NULL,
    storage_key varchar(1000),
    url varchar(2000),
    mime_type varchar(255),
    byte_size bigint,
    width integer,
    height integer,
    original_name varchar(500),
    status varchar(32) NOT NULL DEFAULT 'pending',
    uploaded_by varchar(36),
    ref_count integer NOT NULL DEFAULT 0
);

-- Dedup key: one asset row per (tenant, content hash). A re-upload of identical bytes reuses it.
CREATE UNIQUE INDEX IF NOT EXISTS uq_media_assets_tenant_sha256
    ON media_assets(tenant_id, sha256);
CREATE INDEX IF NOT EXISTS idx_media_assets_tenant_status
    ON media_assets(tenant_id, status);

CREATE TABLE IF NOT EXISTS media_references (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255) NOT NULL,
    media_id varchar(36) NOT NULL,
    owner_type varchar(32) NOT NULL,
    owner_id varchar(36) NOT NULL
);

-- One reference per (media, owner) so ref_count is idempotent under retries.
CREATE UNIQUE INDEX IF NOT EXISTS uq_media_references_media_owner
    ON media_references(media_id, owner_type, owner_id);
CREATE INDEX IF NOT EXISTS idx_media_references_owner
    ON media_references(tenant_id, owner_type, owner_id);
