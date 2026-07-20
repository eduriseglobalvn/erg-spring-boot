CREATE TABLE IF NOT EXISTS content_items (
    id VARCHAR(96) PRIMARY KEY,
    tenant_id VARCHAR(96) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    visibility VARCHAR(32) NOT NULL DEFAULT 'private',
    owner_id VARCHAR(96),
    created_by VARCHAR(96),
    updated_by VARCHAR(96),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS content_assets (
    id VARCHAR(96) PRIMARY KEY,
    tenant_id VARCHAR(96) NOT NULL,
    asset_type VARCHAR(64) NOT NULL,
    title VARCHAR(500),
    storage_provider VARCHAR(64),
    storage_key TEXT,
    storage_url TEXT,
    mime_type VARCHAR(255),
    byte_size BIGINT,
    checksum_sha256 VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(96),
    updated_by VARCHAR(96),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS content_item_assets (
    id VARCHAR(96) PRIMARY KEY,
    tenant_id VARCHAR(96) NOT NULL,
    content_item_id VARCHAR(96) NOT NULL,
    content_asset_id VARCHAR(96) NOT NULL,
    asset_role VARCHAR(64) NOT NULL DEFAULT 'primary',
    sort_order INTEGER NOT NULL DEFAULT 1,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(96),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_content_item_assets_item
        FOREIGN KEY (content_item_id) REFERENCES content_items(id) ON DELETE RESTRICT,
    CONSTRAINT fk_content_item_assets_asset
        FOREIGN KEY (content_asset_id) REFERENCES content_assets(id) ON DELETE RESTRICT,
    CONSTRAINT uq_content_item_assets_role
        UNIQUE (tenant_id, content_item_id, content_asset_id, asset_role)
);

CREATE TABLE IF NOT EXISTS topic_content_items (
    id VARCHAR(96) PRIMARY KEY,
    tenant_id VARCHAR(96) NOT NULL,
    topic_id VARCHAR(96) NOT NULL,
    content_item_id VARCHAR(96) NOT NULL,
    content_role VARCHAR(64) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_by VARCHAR(96),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_topic_content_items_topic
        FOREIGN KEY (topic_id) REFERENCES curriculum_topics(id) ON DELETE RESTRICT,
    CONSTRAINT fk_topic_content_items_content_item
        FOREIGN KEY (content_item_id) REFERENCES content_items(id) ON DELETE RESTRICT,
    CONSTRAINT uq_topic_content_items_item
        UNIQUE (tenant_id, topic_id, content_item_id, content_role)
);

CREATE INDEX IF NOT EXISTS idx_content_items_tenant_status_sort
    ON content_items (tenant_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_content_items_tenant_type_status
    ON content_items (tenant_id, content_type, status);
CREATE INDEX IF NOT EXISTS idx_content_items_tenant_owner
    ON content_items (tenant_id, owner_id);

CREATE INDEX IF NOT EXISTS idx_content_assets_tenant_status
    ON content_assets (tenant_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_content_assets_tenant_checksum
    ON content_assets (tenant_id, checksum_sha256);

CREATE INDEX IF NOT EXISTS idx_content_item_assets_item_sort
    ON content_item_assets (tenant_id, content_item_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_content_item_assets_asset
    ON content_item_assets (tenant_id, content_asset_id);

CREATE INDEX IF NOT EXISTS idx_topic_content_items_topic_sort
    ON topic_content_items (tenant_id, topic_id, status, sort_order);
CREATE INDEX IF NOT EXISTS idx_topic_content_items_content_item
    ON topic_content_items (tenant_id, content_item_id);
