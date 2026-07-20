-- Backfill canonical learning content from the legacy learning-resource tables.
-- This migration is intentionally non-destructive: legacy tables remain the
-- source of truth until all runtime read/write paths have moved to content_*.

INSERT INTO content_items (
    id,
    tenant_id,
    content_type,
    title,
    description,
    status,
    visibility,
    owner_id,
    created_by,
    updated_by,
    created_at,
    updated_at
)
SELECT
    COALESCE(existing_map.new_id, left(md5(resource.tenant_id || ':hoclieu_resources:' || resource.id || ':content_items'), 32)),
    resource.tenant_id,
    COALESCE(NULLIF(upper(resource.metadata ->> 'contentType'), ''), 'LECTURE'),
    resource.title,
    COALESCE(NULLIF(resource.description, ''), NULLIF(resource.subtitle, '')),
    CASE lower(COALESCE(resource.status, 'draft'))
        WHEN 'published' THEN 'published'
        WHEN 'active' THEN 'published'
        WHEN 'archived' THEN 'archived'
        WHEN 'deleted' THEN 'archived'
        ELSE 'draft'
    END,
    CASE lower(COALESCE(resource.visibility, 'private'))
        WHEN 'public' THEN 'public'
        WHEN 'shared' THEN 'shared'
        ELSE 'private'
    END,
    resource.created_by,
    resource.created_by,
    resource.updated_by,
    resource.created_at,
    resource.updated_at
FROM hoclieu_resources resource
LEFT JOIN legacy_id_mappings existing_map
  ON existing_map.tenant_id = resource.tenant_id
 AND existing_map.legacy_table = 'hoclieu_resources'
 AND existing_map.legacy_id = resource.id
 AND existing_map.new_table = 'content_items'
WHERE NOT EXISTS (
    SELECT 1
    FROM content_items existing
    WHERE existing.id = COALESCE(existing_map.new_id, left(md5(resource.tenant_id || ':hoclieu_resources:' || resource.id || ':content_items'), 32))
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO legacy_id_mappings (
    id,
    tenant_id,
    legacy_table,
    legacy_id,
    new_table,
    new_id,
    migration_version,
    confidence,
    created_at
)
SELECT
    left(md5(resource.tenant_id || ':hoclieu_resources:' || resource.id || ':content_items:mapping'), 32),
    resource.tenant_id,
    'hoclieu_resources',
    resource.id,
    'content_items',
    item.id,
    'V39__canonical_content_backfill_from_legacy_resources',
    1.00,
    now()
FROM hoclieu_resources resource
JOIN content_items item
  ON item.id = left(md5(resource.tenant_id || ':hoclieu_resources:' || resource.id || ':content_items'), 32)
WHERE NOT EXISTS (
    SELECT 1
    FROM legacy_id_mappings existing
    WHERE existing.tenant_id = resource.tenant_id
      AND existing.legacy_table = 'hoclieu_resources'
      AND existing.legacy_id = resource.id
      AND existing.new_table = 'content_items'
)
ON CONFLICT (tenant_id, legacy_table, legacy_id, new_table) DO UPDATE SET
    new_id = EXCLUDED.new_id,
    migration_version = EXCLUDED.migration_version,
    confidence = GREATEST(legacy_id_mappings.confidence, EXCLUDED.confidence);

INSERT INTO content_assets (
    id,
    tenant_id,
    asset_type,
    title,
    storage_provider,
    storage_key,
    storage_url,
    mime_type,
    byte_size,
    checksum_sha256,
    status,
    metadata,
    created_by,
    updated_by,
    created_at,
    updated_at
)
SELECT
    COALESCE(existing_map.new_id, left(md5(resource.tenant_id || ':hoclieu_assets:' || asset.id || ':content_assets'), 32)),
    resource.tenant_id,
    CASE
        WHEN asset.google_slide_manifest IS NOT NULL THEN 'GOOGLE_SLIDE'
        WHEN lower(COALESCE(asset.launch_mode, '')) LIKE '%google%' THEN 'GOOGLE_SLIDE'
        WHEN lower(COALESCE(asset.selected_file_type, '')) LIKE '%slide%' THEN 'GOOGLE_SLIDE'
        WHEN lower(COALESCE(asset.file_extension, '')) = 'pdf' THEN 'PDF'
        WHEN lower(COALESCE(asset.detected_mime_type, '')) LIKE 'video/%' THEN 'VIDEO'
        WHEN lower(COALESCE(asset.detected_mime_type, '')) LIKE 'image/%' THEN 'IMAGE'
        ELSE COALESCE(NULLIF(upper(asset.selected_file_type), ''), 'FILE')
    END,
    asset.title,
    asset.storage_provider,
    NULL,
    asset.storage_url,
    asset.detected_mime_type,
    asset.file_size_bytes,
    NULL,
    CASE lower(COALESCE(asset.status, 'active'))
        WHEN 'published' THEN 'active'
        WHEN 'ready' THEN 'active'
        WHEN 'active' THEN 'active'
        WHEN 'deleted' THEN 'archived'
        WHEN 'archived' THEN 'archived'
        ELSE 'draft'
    END,
    jsonb_strip_nulls(jsonb_build_object(
        'legacyAssetId', asset.id,
        'legacyResourceId', asset.resource_id,
        'selectedFileType', asset.selected_file_type,
        'launchMode', asset.launch_mode,
        'originalFileName', asset.original_file_name,
        'fileExtension', asset.file_extension,
        'googleSlideManifest', asset.google_slide_manifest,
        'canDownload', asset.can_download
    )),
    resource.created_by,
    resource.updated_by,
    asset.created_at,
    asset.updated_at
FROM hoclieu_assets asset
JOIN hoclieu_resources resource ON resource.id = asset.resource_id
LEFT JOIN legacy_id_mappings existing_map
  ON existing_map.tenant_id = resource.tenant_id
 AND existing_map.legacy_table = 'hoclieu_assets'
 AND existing_map.legacy_id = asset.id
 AND existing_map.new_table = 'content_assets'
WHERE NOT EXISTS (
    SELECT 1
    FROM content_assets existing
    WHERE existing.id = COALESCE(existing_map.new_id, left(md5(resource.tenant_id || ':hoclieu_assets:' || asset.id || ':content_assets'), 32))
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO legacy_id_mappings (
    id,
    tenant_id,
    legacy_table,
    legacy_id,
    new_table,
    new_id,
    migration_version,
    confidence,
    created_at
)
SELECT
    left(md5(resource.tenant_id || ':hoclieu_assets:' || asset.id || ':content_assets:mapping'), 32),
    resource.tenant_id,
    'hoclieu_assets',
    asset.id,
    'content_assets',
    canonical_asset.id,
    'V39__canonical_content_backfill_from_legacy_resources',
    1.00,
    now()
FROM hoclieu_assets asset
JOIN hoclieu_resources resource ON resource.id = asset.resource_id
JOIN content_assets canonical_asset
  ON canonical_asset.id = left(md5(resource.tenant_id || ':hoclieu_assets:' || asset.id || ':content_assets'), 32)
WHERE NOT EXISTS (
    SELECT 1
    FROM legacy_id_mappings existing
    WHERE existing.tenant_id = resource.tenant_id
      AND existing.legacy_table = 'hoclieu_assets'
      AND existing.legacy_id = asset.id
      AND existing.new_table = 'content_assets'
)
ON CONFLICT (tenant_id, legacy_table, legacy_id, new_table) DO UPDATE SET
    new_id = EXCLUDED.new_id,
    migration_version = EXCLUDED.migration_version,
    confidence = GREATEST(legacy_id_mappings.confidence, EXCLUDED.confidence);

INSERT INTO content_item_assets (
    id,
    tenant_id,
    content_item_id,
    content_asset_id,
    asset_role,
    sort_order,
    metadata,
    created_by,
    created_at
)
SELECT
    left(md5(resource.tenant_id || ':hoclieu_asset_link:' || asset.id), 32),
    resource.tenant_id,
    item_map.new_id,
    asset_map.new_id,
    'primary',
    row_number() OVER (PARTITION BY resource.tenant_id, resource.id ORDER BY asset.created_at, asset.id),
    jsonb_strip_nulls(jsonb_build_object(
        'legacyAssetId', asset.id,
        'legacyResourceId', resource.id,
        'source', 'V39 legacy asset backfill'
    )),
    resource.created_by,
    LEAST(resource.created_at, asset.created_at)
FROM hoclieu_assets asset
JOIN hoclieu_resources resource ON resource.id = asset.resource_id
JOIN legacy_id_mappings item_map
  ON item_map.tenant_id = resource.tenant_id
 AND item_map.legacy_table = 'hoclieu_resources'
 AND item_map.legacy_id = resource.id
 AND item_map.new_table = 'content_items'
JOIN legacy_id_mappings asset_map
  ON asset_map.tenant_id = resource.tenant_id
 AND asset_map.legacy_table = 'hoclieu_assets'
 AND asset_map.legacy_id = asset.id
 AND asset_map.new_table = 'content_assets'
ON CONFLICT ON CONSTRAINT uq_content_item_assets_role DO UPDATE SET
    sort_order = EXCLUDED.sort_order,
    metadata = content_item_assets.metadata || EXCLUDED.metadata;

INSERT INTO content_item_assets (
    id,
    tenant_id,
    content_item_id,
    content_asset_id,
    asset_role,
    sort_order,
    metadata,
    created_by,
    created_at
)
SELECT
    left(md5(item.tenant_id || ':hoclieu_resource_item_link:' || item.id), 32),
    item.tenant_id,
    item_map.new_id,
    asset_map.new_id,
    'primary',
    COALESCE(item.sort_order, 1),
    jsonb_strip_nulls(jsonb_build_object(
        'legacyResourceItemId', item.id,
        'unitTitle', item.unit_title,
        'lessonTitle', item.lesson_title,
        'pageCount', item.page_count,
        'durationSec', item.duration_sec,
        'source', 'V39 legacy resource item backfill'
    )),
    resource.created_by,
    item.created_at
FROM hoclieu_resource_items item
JOIN hoclieu_resources resource ON resource.id = item.resource_id
JOIN legacy_id_mappings item_map
  ON item_map.tenant_id = item.tenant_id
 AND item_map.legacy_table = 'hoclieu_resources'
 AND item_map.legacy_id = item.resource_id
 AND item_map.new_table = 'content_items'
JOIN legacy_id_mappings asset_map
  ON asset_map.tenant_id = item.tenant_id
 AND asset_map.legacy_table = 'hoclieu_assets'
 AND asset_map.legacy_id = item.asset_id
 AND asset_map.new_table = 'content_assets'
WHERE item.asset_id IS NOT NULL
ON CONFLICT ON CONSTRAINT uq_content_item_assets_role DO UPDATE SET
    sort_order = LEAST(content_item_assets.sort_order, EXCLUDED.sort_order),
    metadata = content_item_assets.metadata || EXCLUDED.metadata;

WITH topic_candidates AS (
    SELECT resource.tenant_id, resource.id AS resource_id, resource.topic_id AS legacy_topic_id, 1 AS priority
    FROM hoclieu_resources resource
    WHERE resource.topic_id IS NOT NULL
    UNION ALL
    SELECT resource.tenant_id, resource.id AS resource_id, resource.section_id AS legacy_topic_id, 2 AS priority
    FROM hoclieu_resources resource
    WHERE resource.section_id IS NOT NULL
    UNION ALL
    SELECT resource.tenant_id, resource.id AS resource_id, resource.category_id AS legacy_topic_id, 3 AS priority
    FROM hoclieu_resources resource
    WHERE resource.category_id IS NOT NULL
),
resolved_topics AS (
    SELECT DISTINCT ON (candidate.tenant_id, candidate.resource_id)
        candidate.tenant_id,
        candidate.resource_id,
        topic_map.new_id AS topic_id,
        candidate.priority
    FROM topic_candidates candidate
    JOIN legacy_id_mappings topic_map
      ON topic_map.tenant_id = candidate.tenant_id
     AND topic_map.legacy_table = 'hoclieu_taxonomy'
     AND topic_map.legacy_id = candidate.legacy_topic_id
     AND topic_map.new_table = 'curriculum_topics'
    JOIN curriculum_topics topic
      ON topic.id = topic_map.new_id
     AND topic.tenant_id = candidate.tenant_id
    ORDER BY candidate.tenant_id, candidate.resource_id, candidate.priority
)
INSERT INTO topic_content_items (
    id,
    tenant_id,
    topic_id,
    content_item_id,
    content_role,
    sort_order,
    status,
    created_by,
    created_at
)
SELECT
    left(md5(resource.tenant_id || ':topic_content_item:' || resolved.topic_id || ':' || resource.id || ':LECTURE'), 32),
    resource.tenant_id,
    resolved.topic_id,
    item_map.new_id,
    'LECTURE',
    row_number() OVER (PARTITION BY resource.tenant_id, resolved.topic_id ORDER BY COALESCE(resource.published_at, resource.created_at), resource.title, resource.id),
    CASE lower(COALESCE(resource.status, 'draft'))
        WHEN 'published' THEN 'active'
        WHEN 'active' THEN 'active'
        WHEN 'archived' THEN 'archived'
        WHEN 'deleted' THEN 'archived'
        ELSE 'draft'
    END,
    resource.created_by,
    resource.created_at
FROM resolved_topics resolved
JOIN hoclieu_resources resource
  ON resource.tenant_id = resolved.tenant_id
 AND resource.id = resolved.resource_id
JOIN legacy_id_mappings item_map
  ON item_map.tenant_id = resource.tenant_id
 AND item_map.legacy_table = 'hoclieu_resources'
 AND item_map.legacy_id = resource.id
 AND item_map.new_table = 'content_items'
ON CONFLICT ON CONSTRAINT uq_topic_content_items_item DO UPDATE SET
    sort_order = EXCLUDED.sort_order,
    status = CASE
        WHEN topic_content_items.status = 'active' THEN topic_content_items.status
        ELSE EXCLUDED.status
    END;
