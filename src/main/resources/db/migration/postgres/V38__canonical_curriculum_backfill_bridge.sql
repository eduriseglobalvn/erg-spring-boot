-- V38__canonical_curriculum_backfill_bridge.sql
-- Additive bridge for legacy LMS/HocLieu/question-bank taxonomy to canonical curriculum/content.
-- This migration is intentionally conservative: it never drops, renames, or rewrites legacy tables.

CREATE TABLE IF NOT EXISTS legacy_id_mappings (
    id varchar(96) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    legacy_table varchar(128) NOT NULL,
    legacy_id varchar(255) NOT NULL,
    new_table varchar(128) NOT NULL,
    new_id varchar(255) NOT NULL,
    migration_version varchar(128),
    confidence numeric(5,4) NOT NULL DEFAULT 1.0000,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_legacy_id_mappings_confidence
        CHECK (confidence >= 0 AND confidence <= 1)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_legacy_id_mappings_legacy
    ON legacy_id_mappings(tenant_id, legacy_table, legacy_id, new_table);

CREATE INDEX IF NOT EXISTS idx_legacy_id_mappings_target
    ON legacy_id_mappings(tenant_id, new_table, new_id);

CREATE INDEX IF NOT EXISTS idx_legacy_id_mappings_legacy_table
    ON legacy_id_mappings(legacy_table, legacy_id);

-- LMS subjects -> curriculum_subjects. Prefer preserving the LMS id when it is not already used
-- for a different subject and the canonical tenant/code slot is still available.
DO $$
BEGIN
    IF to_regclass('public.lms_subjects') IS NOT NULL
       AND to_regclass('public.curriculum_subjects') IS NOT NULL THEN
        EXECUTE $sql$
            WITH source_rows AS (
                SELECT
                    src.id,
                    src.tenant_id,
                    COALESCE(NULLIF(BTRIM(src.name), ''), NULLIF(BTRIM(src.code), ''), src.id) AS name,
                    CASE
                        WHEN NULLIF(BTRIM(src.code), '') IS NOT NULL AND length(BTRIM(src.code)) <= 96
                            THEN BTRIM(src.code)
                        ELSE left(
                            lower(regexp_replace(COALESCE(NULLIF(BTRIM(src.code), ''), NULLIF(BTRIM(src.name), ''), src.id), '[^a-zA-Z0-9]+', '-', 'g')),
                            80
                        ) || '-' || left(md5(src.id), 12)
                    END AS code,
                    COALESCE(NULLIF(BTRIM(src.status), ''), 'active') AS status,
                    COALESCE(src.created_at, now()) AS created_at,
                    COALESCE(src.updated_at, now()) AS updated_at,
                    row_number() OVER (
                        PARTITION BY src.tenant_id,
                            CASE
                                WHEN NULLIF(BTRIM(src.code), '') IS NOT NULL AND length(BTRIM(src.code)) <= 96
                                    THEN BTRIM(src.code)
                                ELSE left(
                                    lower(regexp_replace(COALESCE(NULLIF(BTRIM(src.code), ''), NULLIF(BTRIM(src.name), ''), src.id), '[^a-zA-Z0-9]+', '-', 'g')),
                                    80
                                ) || '-' || left(md5(src.id), 12)
                            END
                        ORDER BY src.id
                    ) AS code_rank
                FROM lms_subjects src
                WHERE src.id IS NOT NULL
                  AND src.tenant_id IS NOT NULL
            )
            INSERT INTO curriculum_subjects (
                id, tenant_id, code, name, status, sort_order, created_at, updated_at
            )
            SELECT
                src.id,
                src.tenant_id,
                src.code,
                src.name,
                src.status,
                0,
                src.created_at,
                src.updated_at
            FROM source_rows src
            WHERE src.code_rank = 1
              AND NOT EXISTS (
                  SELECT 1
                  FROM curriculum_subjects existing
                  WHERE existing.id = src.id
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM curriculum_subjects existing
                  WHERE existing.tenant_id = src.tenant_id
                    AND existing.code = src.code
              )
            ON CONFLICT DO NOTHING
        $sql$;

        EXECUTE $sql$
            WITH source_rows AS (
                SELECT
                    src.id,
                    src.tenant_id,
                    CASE
                        WHEN NULLIF(BTRIM(src.code), '') IS NOT NULL AND length(BTRIM(src.code)) <= 96
                            THEN BTRIM(src.code)
                        ELSE left(
                            lower(regexp_replace(COALESCE(NULLIF(BTRIM(src.code), ''), NULLIF(BTRIM(src.name), ''), src.id), '[^a-zA-Z0-9]+', '-', 'g')),
                            80
                        ) || '-' || left(md5(src.id), 12)
                    END AS code,
                    COALESCE(NULLIF(BTRIM(src.name), ''), NULLIF(BTRIM(src.code), ''), src.id) AS name
                FROM lms_subjects src
                WHERE src.id IS NOT NULL
                  AND src.tenant_id IS NOT NULL
            ),
            matched AS (
                SELECT DISTINCT ON (src.tenant_id, src.id)
                    src.tenant_id,
                    src.id AS legacy_id,
                    canonical.id AS new_id,
                    CASE
                        WHEN canonical.id = src.id THEN 1.0000::numeric
                        WHEN canonical.code = src.code THEN 0.9500::numeric
                        ELSE 0.8500::numeric
                    END AS confidence
                FROM source_rows src
                JOIN curriculum_subjects canonical
                  ON canonical.tenant_id = src.tenant_id
                 AND (
                    canonical.id = src.id
                    OR canonical.code = src.code
                    OR lower(canonical.name) = lower(src.name)
                 )
                ORDER BY src.tenant_id, src.id,
                    CASE
                        WHEN canonical.id = src.id THEN 1
                        WHEN canonical.code = src.code THEN 2
                        ELSE 3
                    END
            )
            INSERT INTO legacy_id_mappings (
                id, tenant_id, legacy_table, legacy_id, new_table, new_id, confidence, created_at
            )
            SELECT
                left(md5(tenant_id || ':lms_subjects:' || legacy_id || ':curriculum_subjects'), 32),
                tenant_id,
                'lms_subjects',
                legacy_id,
                'curriculum_subjects',
                new_id,
                confidence,
                now()
            FROM matched
            ON CONFLICT (tenant_id, legacy_table, legacy_id, new_table) DO UPDATE SET
                new_id = EXCLUDED.new_id,
                confidence = GREATEST(legacy_id_mappings.confidence, EXCLUDED.confidence)
        $sql$;
    END IF;
END $$;

-- LMS levels -> curriculum_levels.
DO $$
BEGIN
    IF to_regclass('public.lms_levels') IS NOT NULL
       AND to_regclass('public.curriculum_levels') IS NOT NULL THEN
        EXECUTE $sql$
            WITH source_rows AS (
                SELECT
                    lvl.id,
                    lvl.tenant_id,
                    subject_map.new_id AS subject_id,
                    COALESCE(NULLIF(BTRIM(lvl.name), ''), NULLIF(BTRIM(lvl.code), ''), lvl.id) AS name,
                    CASE
                        WHEN NULLIF(BTRIM(lvl.code), '') IS NOT NULL AND length(BTRIM(lvl.code)) <= 96
                            THEN BTRIM(lvl.code)
                        ELSE left(
                            lower(regexp_replace(COALESCE(NULLIF(BTRIM(lvl.code), ''), NULLIF(BTRIM(lvl.name), ''), lvl.id), '[^a-zA-Z0-9]+', '-', 'g')),
                            80
                        ) || '-' || left(md5(lvl.id), 12)
                    END AS code,
                    COALESCE(NULLIF(BTRIM(lvl.status), ''), 'active') AS status,
                    COALESCE(lvl.sort_order, 0) AS sort_order,
                    COALESCE(lvl.created_at, now()) AS created_at,
                    COALESCE(lvl.updated_at, now()) AS updated_at,
                    row_number() OVER (
                        PARTITION BY lvl.tenant_id, subject_map.new_id,
                            CASE
                                WHEN NULLIF(BTRIM(lvl.code), '') IS NOT NULL AND length(BTRIM(lvl.code)) <= 96
                                    THEN BTRIM(lvl.code)
                                ELSE left(
                                    lower(regexp_replace(COALESCE(NULLIF(BTRIM(lvl.code), ''), NULLIF(BTRIM(lvl.name), ''), lvl.id), '[^a-zA-Z0-9]+', '-', 'g')),
                                    80
                                ) || '-' || left(md5(lvl.id), 12)
                            END
                        ORDER BY lvl.id
                    ) AS code_rank
                FROM lms_levels lvl
                JOIN legacy_id_mappings subject_map
                  ON subject_map.tenant_id = lvl.tenant_id
                 AND subject_map.legacy_table = 'lms_subjects'
                 AND subject_map.legacy_id = lvl.subject_id
                 AND subject_map.new_table = 'curriculum_subjects'
                WHERE lvl.id IS NOT NULL
                  AND lvl.tenant_id IS NOT NULL
            )
            INSERT INTO curriculum_levels (
                id, tenant_id, subject_id, code, name, status, sort_order, created_at, updated_at
            )
            SELECT
                src.id,
                src.tenant_id,
                src.subject_id,
                src.code,
                src.name,
                src.status,
                src.sort_order,
                src.created_at,
                src.updated_at
            FROM source_rows src
            WHERE src.code_rank = 1
              AND NOT EXISTS (SELECT 1 FROM curriculum_levels existing WHERE existing.id = src.id)
              AND NOT EXISTS (
                  SELECT 1
                  FROM curriculum_levels existing
                  WHERE existing.tenant_id = src.tenant_id
                    AND existing.subject_id = src.subject_id
                    AND existing.code = src.code
              )
            ON CONFLICT DO NOTHING
        $sql$;

        EXECUTE $sql$
            WITH source_rows AS (
                SELECT
                    lvl.id,
                    lvl.tenant_id,
                    subject_map.new_id AS subject_id,
                    COALESCE(NULLIF(BTRIM(lvl.name), ''), NULLIF(BTRIM(lvl.code), ''), lvl.id) AS name,
                    CASE
                        WHEN NULLIF(BTRIM(lvl.code), '') IS NOT NULL AND length(BTRIM(lvl.code)) <= 96
                            THEN BTRIM(lvl.code)
                        ELSE left(
                            lower(regexp_replace(COALESCE(NULLIF(BTRIM(lvl.code), ''), NULLIF(BTRIM(lvl.name), ''), lvl.id), '[^a-zA-Z0-9]+', '-', 'g')),
                            80
                        ) || '-' || left(md5(lvl.id), 12)
                    END AS code
                FROM lms_levels lvl
                JOIN legacy_id_mappings subject_map
                  ON subject_map.tenant_id = lvl.tenant_id
                 AND subject_map.legacy_table = 'lms_subjects'
                 AND subject_map.legacy_id = lvl.subject_id
                 AND subject_map.new_table = 'curriculum_subjects'
                WHERE lvl.id IS NOT NULL
                  AND lvl.tenant_id IS NOT NULL
            ),
            matched AS (
                SELECT DISTINCT ON (src.tenant_id, src.id)
                    src.tenant_id,
                    src.id AS legacy_id,
                    canonical.id AS new_id,
                    CASE
                        WHEN canonical.id = src.id THEN 1.0000::numeric
                        WHEN canonical.code = src.code THEN 0.9500::numeric
                        ELSE 0.8500::numeric
                    END AS confidence
                FROM source_rows src
                JOIN curriculum_levels canonical
                  ON canonical.tenant_id = src.tenant_id
                 AND canonical.subject_id = src.subject_id
                 AND (
                    canonical.id = src.id
                    OR canonical.code = src.code
                    OR lower(canonical.name) = lower(src.name)
                 )
                ORDER BY src.tenant_id, src.id,
                    CASE
                        WHEN canonical.id = src.id THEN 1
                        WHEN canonical.code = src.code THEN 2
                        ELSE 3
                    END
            )
            INSERT INTO legacy_id_mappings (
                id, tenant_id, legacy_table, legacy_id, new_table, new_id, confidence, created_at
            )
            SELECT
                left(md5(tenant_id || ':lms_levels:' || legacy_id || ':curriculum_levels'), 32),
                tenant_id,
                'lms_levels',
                legacy_id,
                'curriculum_levels',
                new_id,
                confidence,
                now()
            FROM matched
            ON CONFLICT (tenant_id, legacy_table, legacy_id, new_table) DO UPDATE SET
                new_id = EXCLUDED.new_id,
                confidence = GREATEST(legacy_id_mappings.confidence, EXCLUDED.confidence)
        $sql$;
    END IF;
END $$;

-- LMS topics -> curriculum_topics.
DO $$
BEGIN
    IF to_regclass('public.lms_topics') IS NOT NULL
       AND to_regclass('public.curriculum_topics') IS NOT NULL THEN
        EXECUTE $sql$
            WITH source_rows AS (
                SELECT
                    topic.id,
                    topic.tenant_id,
                    level_map.new_id AS level_id,
                    COALESCE(NULLIF(BTRIM(topic.name), ''), NULLIF(BTRIM(topic.code), ''), topic.id) AS name,
                    CASE
                        WHEN NULLIF(BTRIM(topic.code), '') IS NOT NULL AND length(BTRIM(topic.code)) <= 96
                            THEN BTRIM(topic.code)
                        ELSE left(
                            lower(regexp_replace(COALESCE(NULLIF(BTRIM(topic.code), ''), NULLIF(BTRIM(topic.name), ''), topic.id), '[^a-zA-Z0-9]+', '-', 'g')),
                            80
                        ) || '-' || left(md5(topic.id), 12)
                    END AS code,
                    COALESCE(NULLIF(BTRIM(topic.status), ''), 'active') AS status,
                    COALESCE(topic.sort_order, 0) AS sort_order,
                    COALESCE(topic.created_at, now()) AS created_at,
                    COALESCE(topic.updated_at, now()) AS updated_at,
                    row_number() OVER (
                        PARTITION BY topic.tenant_id, level_map.new_id,
                            CASE
                                WHEN NULLIF(BTRIM(topic.code), '') IS NOT NULL AND length(BTRIM(topic.code)) <= 96
                                    THEN BTRIM(topic.code)
                                ELSE left(
                                    lower(regexp_replace(COALESCE(NULLIF(BTRIM(topic.code), ''), NULLIF(BTRIM(topic.name), ''), topic.id), '[^a-zA-Z0-9]+', '-', 'g')),
                                    80
                                ) || '-' || left(md5(topic.id), 12)
                            END
                        ORDER BY topic.id
                    ) AS code_rank
                FROM lms_topics topic
                JOIN legacy_id_mappings level_map
                  ON level_map.tenant_id = topic.tenant_id
                 AND level_map.legacy_table = 'lms_levels'
                 AND level_map.legacy_id = topic.level_id
                 AND level_map.new_table = 'curriculum_levels'
                WHERE topic.id IS NOT NULL
                  AND topic.tenant_id IS NOT NULL
            )
            INSERT INTO curriculum_topics (
                id, tenant_id, level_id, code, name, status, sort_order, created_at, updated_at
            )
            SELECT
                src.id,
                src.tenant_id,
                src.level_id,
                src.code,
                src.name,
                src.status,
                src.sort_order,
                src.created_at,
                src.updated_at
            FROM source_rows src
            WHERE src.code_rank = 1
              AND NOT EXISTS (SELECT 1 FROM curriculum_topics existing WHERE existing.id = src.id)
              AND NOT EXISTS (
                  SELECT 1
                  FROM curriculum_topics existing
                  WHERE existing.tenant_id = src.tenant_id
                    AND existing.level_id = src.level_id
                    AND existing.code = src.code
              )
            ON CONFLICT DO NOTHING
        $sql$;

        EXECUTE $sql$
            WITH source_rows AS (
                SELECT
                    topic.id,
                    topic.tenant_id,
                    level_map.new_id AS level_id,
                    COALESCE(NULLIF(BTRIM(topic.name), ''), NULLIF(BTRIM(topic.code), ''), topic.id) AS name,
                    CASE
                        WHEN NULLIF(BTRIM(topic.code), '') IS NOT NULL AND length(BTRIM(topic.code)) <= 96
                            THEN BTRIM(topic.code)
                        ELSE left(
                            lower(regexp_replace(COALESCE(NULLIF(BTRIM(topic.code), ''), NULLIF(BTRIM(topic.name), ''), topic.id), '[^a-zA-Z0-9]+', '-', 'g')),
                            80
                        ) || '-' || left(md5(topic.id), 12)
                    END AS code
                FROM lms_topics topic
                JOIN legacy_id_mappings level_map
                  ON level_map.tenant_id = topic.tenant_id
                 AND level_map.legacy_table = 'lms_levels'
                 AND level_map.legacy_id = topic.level_id
                 AND level_map.new_table = 'curriculum_levels'
                WHERE topic.id IS NOT NULL
                  AND topic.tenant_id IS NOT NULL
            ),
            matched AS (
                SELECT DISTINCT ON (src.tenant_id, src.id)
                    src.tenant_id,
                    src.id AS legacy_id,
                    canonical.id AS new_id,
                    CASE
                        WHEN canonical.id = src.id THEN 1.0000::numeric
                        WHEN canonical.code = src.code THEN 0.9500::numeric
                        ELSE 0.8500::numeric
                    END AS confidence
                FROM source_rows src
                JOIN curriculum_topics canonical
                  ON canonical.tenant_id = src.tenant_id
                 AND canonical.level_id = src.level_id
                 AND (
                    canonical.id = src.id
                    OR canonical.code = src.code
                    OR lower(canonical.name) = lower(src.name)
                 )
                ORDER BY src.tenant_id, src.id,
                    CASE
                        WHEN canonical.id = src.id THEN 1
                        WHEN canonical.code = src.code THEN 2
                        ELSE 3
                    END
            )
            INSERT INTO legacy_id_mappings (
                id, tenant_id, legacy_table, legacy_id, new_table, new_id, confidence, created_at
            )
            SELECT
                left(md5(tenant_id || ':lms_topics:' || legacy_id || ':curriculum_topics'), 32),
                tenant_id,
                'lms_topics',
                legacy_id,
                'curriculum_topics',
                new_id,
                confidence,
                now()
            FROM matched
            ON CONFLICT (tenant_id, legacy_table, legacy_id, new_table) DO UPDATE SET
                new_id = EXCLUDED.new_id,
                confidence = GREATEST(legacy_id_mappings.confidence, EXCLUDED.confidence)
        $sql$;
    END IF;
END $$;

-- HocLieu subject taxonomy -> curriculum_subjects.
DO $$
BEGIN
    IF to_regclass('public.hoclieu_taxonomy') IS NOT NULL
       AND to_regclass('public.curriculum_subjects') IS NOT NULL THEN
        EXECUTE $sql$
            WITH source_rows AS (
                SELECT
                    tax.id,
                    CASE
                        WHEN length(tax.id) <= 36 THEN tax.id
                        ELSE left(md5('hoclieu_taxonomy:subject:' || tax.id), 32)
                    END AS canonical_id,
                    tax.tenant_id,
                    COALESCE(NULLIF(BTRIM(tax.label), ''), NULLIF(BTRIM(tax.slug), ''), tax.id) AS name,
                    CASE
                        WHEN NULLIF(BTRIM(tax.slug), '') IS NOT NULL AND length(BTRIM(tax.slug)) <= 96
                            THEN BTRIM(tax.slug)
                        ELSE left(
                            lower(regexp_replace(COALESCE(NULLIF(BTRIM(tax.slug), ''), NULLIF(BTRIM(tax.label), ''), tax.id), '[^a-zA-Z0-9]+', '-', 'g')),
                            80
                        ) || '-' || left(md5(tax.id), 12)
                    END AS code,
                    COALESCE(NULLIF(BTRIM(tax.description), ''), NULL) AS description,
                    COALESCE(NULLIF(BTRIM(tax.status), ''), 'active') AS status,
                    COALESCE(tax.sort_order, 0) AS sort_order,
                    COALESCE(tax.created_at, now()) AS created_at,
                    COALESCE(tax.updated_at, now()) AS updated_at,
                    row_number() OVER (
                        PARTITION BY tax.tenant_id,
                            CASE
                                WHEN NULLIF(BTRIM(tax.slug), '') IS NOT NULL AND length(BTRIM(tax.slug)) <= 96
                                    THEN BTRIM(tax.slug)
                                ELSE left(
                                    lower(regexp_replace(COALESCE(NULLIF(BTRIM(tax.slug), ''), NULLIF(BTRIM(tax.label), ''), tax.id), '[^a-zA-Z0-9]+', '-', 'g')),
                                    80
                                ) || '-' || left(md5(tax.id), 12)
                            END
                        ORDER BY tax.id
                    ) AS code_rank
                FROM hoclieu_taxonomy tax
                WHERE tax.id IS NOT NULL
                  AND tax.tenant_id IS NOT NULL
                  AND lower(tax.kind) = 'subject'
            )
            INSERT INTO curriculum_subjects (
                id, tenant_id, code, name, description, status, sort_order, created_at, updated_at
            )
            SELECT
                src.canonical_id,
                src.tenant_id,
                src.code,
                src.name,
                src.description,
                src.status,
                src.sort_order,
                src.created_at,
                src.updated_at
            FROM source_rows src
            WHERE src.code_rank = 1
              AND NOT EXISTS (SELECT 1 FROM curriculum_subjects existing WHERE existing.id = src.canonical_id)
              AND NOT EXISTS (
                  SELECT 1
                  FROM curriculum_subjects existing
                  WHERE existing.tenant_id = src.tenant_id
                    AND existing.code = src.code
              )
            ON CONFLICT DO NOTHING
        $sql$;

        EXECUTE $sql$
            WITH source_rows AS (
                SELECT
                    tax.id,
                    CASE
                        WHEN length(tax.id) <= 36 THEN tax.id
                        ELSE left(md5('hoclieu_taxonomy:subject:' || tax.id), 32)
                    END AS canonical_id,
                    tax.tenant_id,
                    COALESCE(NULLIF(BTRIM(tax.label), ''), NULLIF(BTRIM(tax.slug), ''), tax.id) AS name,
                    CASE
                        WHEN NULLIF(BTRIM(tax.slug), '') IS NOT NULL AND length(BTRIM(tax.slug)) <= 96
                            THEN BTRIM(tax.slug)
                        ELSE left(
                            lower(regexp_replace(COALESCE(NULLIF(BTRIM(tax.slug), ''), NULLIF(BTRIM(tax.label), ''), tax.id), '[^a-zA-Z0-9]+', '-', 'g')),
                            80
                        ) || '-' || left(md5(tax.id), 12)
                    END AS code
                FROM hoclieu_taxonomy tax
                WHERE tax.id IS NOT NULL
                  AND tax.tenant_id IS NOT NULL
                  AND lower(tax.kind) = 'subject'
            ),
            matched AS (
                SELECT DISTINCT ON (src.tenant_id, src.id)
                    src.tenant_id,
                    src.id AS legacy_id,
                    canonical.id AS new_id,
                    CASE
                        WHEN canonical.id = src.canonical_id THEN 1.0000::numeric
                        WHEN canonical.code = src.code THEN 0.9000::numeric
                        ELSE 0.8000::numeric
                    END AS confidence
                FROM source_rows src
                JOIN curriculum_subjects canonical
                  ON canonical.tenant_id = src.tenant_id
                 AND (
                    canonical.id = src.canonical_id
                    OR canonical.code = src.code
                    OR lower(canonical.name) = lower(src.name)
                 )
                ORDER BY src.tenant_id, src.id,
                    CASE
                        WHEN canonical.id = src.canonical_id THEN 1
                        WHEN canonical.code = src.code THEN 2
                        ELSE 3
                    END
            )
            INSERT INTO legacy_id_mappings (
                id, tenant_id, legacy_table, legacy_id, new_table, new_id, confidence, created_at
            )
            SELECT
                left(md5(tenant_id || ':hoclieu_taxonomy:' || legacy_id || ':curriculum_subjects'), 32),
                tenant_id,
                'hoclieu_taxonomy',
                legacy_id,
                'curriculum_subjects',
                new_id,
                confidence,
                now()
            FROM matched
            ON CONFLICT (tenant_id, legacy_table, legacy_id, new_table) DO UPDATE SET
                new_id = EXCLUDED.new_id,
                confidence = GREATEST(legacy_id_mappings.confidence, EXCLUDED.confidence)
        $sql$;
    END IF;
END $$;

-- HocLieu category taxonomy -> curriculum_levels, only when a subject can be inferred.
DO $$
BEGIN
    IF to_regclass('public.hoclieu_taxonomy') IS NOT NULL
       AND to_regclass('public.curriculum_levels') IS NOT NULL THEN
        EXECUTE $sql$
            WITH source_rows AS (
                SELECT
                    tax.id,
                    CASE
                        WHEN length(tax.id) <= 36 THEN tax.id
                        ELSE left(md5('hoclieu_taxonomy:category:' || tax.id), 32)
                    END AS canonical_id,
                    tax.tenant_id,
                    subject_map.new_id AS subject_id,
                    COALESCE(NULLIF(BTRIM(tax.label), ''), NULLIF(BTRIM(tax.slug), ''), tax.id) AS name,
                    CASE
                        WHEN NULLIF(BTRIM(tax.slug), '') IS NOT NULL AND length(BTRIM(tax.slug)) <= 96
                            THEN BTRIM(tax.slug)
                        ELSE left(
                            lower(regexp_replace(COALESCE(NULLIF(BTRIM(tax.slug), ''), NULLIF(BTRIM(tax.label), ''), tax.id), '[^a-zA-Z0-9]+', '-', 'g')),
                            80
                        ) || '-' || left(md5(tax.id), 12)
                    END AS code,
                    COALESCE(NULLIF(BTRIM(tax.description), ''), NULL) AS description,
                    COALESCE(NULLIF(BTRIM(tax.status), ''), 'active') AS status,
                    COALESCE(tax.sort_order, 0) AS sort_order,
                    COALESCE(tax.created_at, now()) AS created_at,
                    COALESCE(tax.updated_at, now()) AS updated_at,
                    row_number() OVER (
                        PARTITION BY tax.tenant_id, subject_map.new_id,
                            CASE
                                WHEN NULLIF(BTRIM(tax.slug), '') IS NOT NULL AND length(BTRIM(tax.slug)) <= 96
                                    THEN BTRIM(tax.slug)
                                ELSE left(
                                    lower(regexp_replace(COALESCE(NULLIF(BTRIM(tax.slug), ''), NULLIF(BTRIM(tax.label), ''), tax.id), '[^a-zA-Z0-9]+', '-', 'g')),
                                    80
                                ) || '-' || left(md5(tax.id), 12)
                            END
                        ORDER BY tax.id
                    ) AS code_rank
                FROM hoclieu_taxonomy tax
                JOIN legacy_id_mappings subject_map
                  ON subject_map.tenant_id = tax.tenant_id
                 AND subject_map.legacy_table = 'hoclieu_taxonomy'
                 AND subject_map.legacy_id = COALESCE(tax.subject_id, tax.parent_id)
                 AND subject_map.new_table = 'curriculum_subjects'
                WHERE tax.id IS NOT NULL
                  AND tax.tenant_id IS NOT NULL
                  AND lower(tax.kind) = 'category'
            )
            INSERT INTO curriculum_levels (
                id, tenant_id, subject_id, code, name, description, status, sort_order, created_at, updated_at
            )
            SELECT
                src.canonical_id,
                src.tenant_id,
                src.subject_id,
                src.code,
                src.name,
                src.description,
                src.status,
                src.sort_order,
                src.created_at,
                src.updated_at
            FROM source_rows src
            WHERE src.code_rank = 1
              AND NOT EXISTS (SELECT 1 FROM curriculum_levels existing WHERE existing.id = src.canonical_id)
              AND NOT EXISTS (
                  SELECT 1
                  FROM curriculum_levels existing
                  WHERE existing.tenant_id = src.tenant_id
                    AND existing.subject_id = src.subject_id
                    AND existing.code = src.code
              )
            ON CONFLICT DO NOTHING
        $sql$;

        EXECUTE $sql$
            WITH source_rows AS (
                SELECT
                    tax.id,
                    CASE
                        WHEN length(tax.id) <= 36 THEN tax.id
                        ELSE left(md5('hoclieu_taxonomy:category:' || tax.id), 32)
                    END AS canonical_id,
                    tax.tenant_id,
                    subject_map.new_id AS subject_id,
                    COALESCE(NULLIF(BTRIM(tax.label), ''), NULLIF(BTRIM(tax.slug), ''), tax.id) AS name,
                    CASE
                        WHEN NULLIF(BTRIM(tax.slug), '') IS NOT NULL AND length(BTRIM(tax.slug)) <= 96
                            THEN BTRIM(tax.slug)
                        ELSE left(
                            lower(regexp_replace(COALESCE(NULLIF(BTRIM(tax.slug), ''), NULLIF(BTRIM(tax.label), ''), tax.id), '[^a-zA-Z0-9]+', '-', 'g')),
                            80
                        ) || '-' || left(md5(tax.id), 12)
                    END AS code
                FROM hoclieu_taxonomy tax
                JOIN legacy_id_mappings subject_map
                  ON subject_map.tenant_id = tax.tenant_id
                 AND subject_map.legacy_table = 'hoclieu_taxonomy'
                 AND subject_map.legacy_id = COALESCE(tax.subject_id, tax.parent_id)
                 AND subject_map.new_table = 'curriculum_subjects'
                WHERE tax.id IS NOT NULL
                  AND tax.tenant_id IS NOT NULL
                  AND lower(tax.kind) = 'category'
            ),
            matched AS (
                SELECT DISTINCT ON (src.tenant_id, src.id)
                    src.tenant_id,
                    src.id AS legacy_id,
                    canonical.id AS new_id,
                    CASE
                        WHEN canonical.id = src.canonical_id THEN 1.0000::numeric
                        WHEN canonical.code = src.code THEN 0.9000::numeric
                        ELSE 0.8000::numeric
                    END AS confidence
                FROM source_rows src
                JOIN curriculum_levels canonical
                  ON canonical.tenant_id = src.tenant_id
                 AND canonical.subject_id = src.subject_id
                 AND (
                    canonical.id = src.canonical_id
                    OR canonical.code = src.code
                    OR lower(canonical.name) = lower(src.name)
                 )
                ORDER BY src.tenant_id, src.id,
                    CASE
                        WHEN canonical.id = src.canonical_id THEN 1
                        WHEN canonical.code = src.code THEN 2
                        ELSE 3
                    END
            )
            INSERT INTO legacy_id_mappings (
                id, tenant_id, legacy_table, legacy_id, new_table, new_id, confidence, created_at
            )
            SELECT
                left(md5(tenant_id || ':hoclieu_taxonomy:' || legacy_id || ':curriculum_levels'), 32),
                tenant_id,
                'hoclieu_taxonomy',
                legacy_id,
                'curriculum_levels',
                new_id,
                confidence,
                now()
            FROM matched
            ON CONFLICT (tenant_id, legacy_table, legacy_id, new_table) DO UPDATE SET
                new_id = EXCLUDED.new_id,
                confidence = GREATEST(legacy_id_mappings.confidence, EXCLUDED.confidence)
        $sql$;
    END IF;
END $$;

-- HocLieu section taxonomy -> curriculum_topics, only when a category/level can be inferred.
DO $$
BEGIN
    IF to_regclass('public.hoclieu_taxonomy') IS NOT NULL
       AND to_regclass('public.curriculum_topics') IS NOT NULL THEN
        EXECUTE $sql$
            WITH source_rows AS (
                SELECT
                    tax.id,
                    CASE
                        WHEN length(tax.id) <= 36 THEN tax.id
                        ELSE left(md5('hoclieu_taxonomy:section:' || tax.id), 32)
                    END AS canonical_id,
                    tax.tenant_id,
                    level_map.new_id AS level_id,
                    COALESCE(NULLIF(BTRIM(tax.label), ''), NULLIF(BTRIM(tax.slug), ''), tax.id) AS name,
                    CASE
                        WHEN NULLIF(BTRIM(tax.slug), '') IS NOT NULL AND length(BTRIM(tax.slug)) <= 96
                            THEN BTRIM(tax.slug)
                        ELSE left(
                            lower(regexp_replace(COALESCE(NULLIF(BTRIM(tax.slug), ''), NULLIF(BTRIM(tax.label), ''), tax.id), '[^a-zA-Z0-9]+', '-', 'g')),
                            80
                        ) || '-' || left(md5(tax.id), 12)
                    END AS code,
                    COALESCE(NULLIF(BTRIM(tax.description), ''), NULL) AS description,
                    COALESCE(NULLIF(BTRIM(tax.status), ''), 'active') AS status,
                    COALESCE(tax.sort_order, 0) AS sort_order,
                    COALESCE(tax.created_at, now()) AS created_at,
                    COALESCE(tax.updated_at, now()) AS updated_at,
                    row_number() OVER (
                        PARTITION BY tax.tenant_id, level_map.new_id,
                            CASE
                                WHEN NULLIF(BTRIM(tax.slug), '') IS NOT NULL AND length(BTRIM(tax.slug)) <= 96
                                    THEN BTRIM(tax.slug)
                                ELSE left(
                                    lower(regexp_replace(COALESCE(NULLIF(BTRIM(tax.slug), ''), NULLIF(BTRIM(tax.label), ''), tax.id), '[^a-zA-Z0-9]+', '-', 'g')),
                                    80
                                ) || '-' || left(md5(tax.id), 12)
                            END
                        ORDER BY tax.id
                    ) AS code_rank
                FROM hoclieu_taxonomy tax
                JOIN legacy_id_mappings level_map
                  ON level_map.tenant_id = tax.tenant_id
                 AND level_map.legacy_table = 'hoclieu_taxonomy'
                 AND level_map.legacy_id = COALESCE(tax.category_id, tax.parent_id)
                 AND level_map.new_table = 'curriculum_levels'
                WHERE tax.id IS NOT NULL
                  AND tax.tenant_id IS NOT NULL
                  AND lower(tax.kind) = 'section'
            )
            INSERT INTO curriculum_topics (
                id, tenant_id, level_id, code, name, description, status, sort_order, created_at, updated_at
            )
            SELECT
                src.canonical_id,
                src.tenant_id,
                src.level_id,
                src.code,
                src.name,
                src.description,
                src.status,
                src.sort_order,
                src.created_at,
                src.updated_at
            FROM source_rows src
            WHERE src.code_rank = 1
              AND NOT EXISTS (SELECT 1 FROM curriculum_topics existing WHERE existing.id = src.canonical_id)
              AND NOT EXISTS (
                  SELECT 1
                  FROM curriculum_topics existing
                  WHERE existing.tenant_id = src.tenant_id
                    AND existing.level_id = src.level_id
                    AND existing.code = src.code
              )
            ON CONFLICT DO NOTHING
        $sql$;

        EXECUTE $sql$
            WITH source_rows AS (
                SELECT
                    tax.id,
                    CASE
                        WHEN length(tax.id) <= 36 THEN tax.id
                        ELSE left(md5('hoclieu_taxonomy:section:' || tax.id), 32)
                    END AS canonical_id,
                    tax.tenant_id,
                    level_map.new_id AS level_id,
                    COALESCE(NULLIF(BTRIM(tax.label), ''), NULLIF(BTRIM(tax.slug), ''), tax.id) AS name,
                    CASE
                        WHEN NULLIF(BTRIM(tax.slug), '') IS NOT NULL AND length(BTRIM(tax.slug)) <= 96
                            THEN BTRIM(tax.slug)
                        ELSE left(
                            lower(regexp_replace(COALESCE(NULLIF(BTRIM(tax.slug), ''), NULLIF(BTRIM(tax.label), ''), tax.id), '[^a-zA-Z0-9]+', '-', 'g')),
                            80
                        ) || '-' || left(md5(tax.id), 12)
                    END AS code
                FROM hoclieu_taxonomy tax
                JOIN legacy_id_mappings level_map
                  ON level_map.tenant_id = tax.tenant_id
                 AND level_map.legacy_table = 'hoclieu_taxonomy'
                 AND level_map.legacy_id = COALESCE(tax.category_id, tax.parent_id)
                 AND level_map.new_table = 'curriculum_levels'
                WHERE tax.id IS NOT NULL
                  AND tax.tenant_id IS NOT NULL
                  AND lower(tax.kind) = 'section'
            ),
            matched AS (
                SELECT DISTINCT ON (src.tenant_id, src.id)
                    src.tenant_id,
                    src.id AS legacy_id,
                    canonical.id AS new_id,
                    CASE
                        WHEN canonical.id = src.canonical_id THEN 1.0000::numeric
                        WHEN canonical.code = src.code THEN 0.9000::numeric
                        ELSE 0.8000::numeric
                    END AS confidence
                FROM source_rows src
                JOIN curriculum_topics canonical
                  ON canonical.tenant_id = src.tenant_id
                 AND canonical.level_id = src.level_id
                 AND (
                    canonical.id = src.canonical_id
                    OR canonical.code = src.code
                    OR lower(canonical.name) = lower(src.name)
                 )
                ORDER BY src.tenant_id, src.id,
                    CASE
                        WHEN canonical.id = src.canonical_id THEN 1
                        WHEN canonical.code = src.code THEN 2
                        ELSE 3
                    END
            )
            INSERT INTO legacy_id_mappings (
                id, tenant_id, legacy_table, legacy_id, new_table, new_id, confidence, created_at
            )
            SELECT
                left(md5(tenant_id || ':hoclieu_taxonomy:' || legacy_id || ':curriculum_topics'), 32),
                tenant_id,
                'hoclieu_taxonomy',
                legacy_id,
                'curriculum_topics',
                new_id,
                confidence,
                now()
            FROM matched
            ON CONFLICT (tenant_id, legacy_table, legacy_id, new_table) DO UPDATE SET
                new_id = EXCLUDED.new_id,
                confidence = GREATEST(legacy_id_mappings.confidence, EXCLUDED.confidence)
        $sql$;
    END IF;
END $$;

-- Question-bank taxonomy mapping only. Do not create canonical rows from question_bank_* because
-- those tables are authoring filters and can be less curated than LMS/HocLieu source taxonomy.
DO $$
BEGIN
    IF to_regclass('public.question_bank_subjects') IS NOT NULL
       AND to_regclass('public.curriculum_subjects') IS NOT NULL THEN
        EXECUTE $sql$
            WITH matched AS (
                SELECT DISTINCT ON (bank.tenant_id, bank.id)
                    bank.tenant_id,
                    bank.id AS legacy_id,
                    canonical.id AS new_id,
                    CASE
                        WHEN canonical.id = bank.id THEN 1.0000::numeric
                        ELSE 0.8000::numeric
                    END AS confidence
                FROM question_bank_subjects bank
                JOIN curriculum_subjects canonical
                  ON canonical.tenant_id = bank.tenant_id
                 AND (
                    canonical.id = bank.id
                    OR lower(canonical.code) = lower(BTRIM(bank.label))
                    OR lower(canonical.name) = lower(BTRIM(bank.label))
                 )
                WHERE bank.id IS NOT NULL
                  AND bank.tenant_id IS NOT NULL
                  AND NULLIF(BTRIM(bank.label), '') IS NOT NULL
                ORDER BY bank.tenant_id, bank.id,
                    CASE WHEN canonical.id = bank.id THEN 1 ELSE 2 END
            )
            INSERT INTO legacy_id_mappings (
                id, tenant_id, legacy_table, legacy_id, new_table, new_id, confidence, created_at
            )
            SELECT
                left(md5(tenant_id || ':question_bank_subjects:' || legacy_id || ':curriculum_subjects'), 32),
                tenant_id,
                'question_bank_subjects',
                legacy_id,
                'curriculum_subjects',
                new_id,
                confidence,
                now()
            FROM matched
            ON CONFLICT (tenant_id, legacy_table, legacy_id, new_table) DO UPDATE SET
                new_id = EXCLUDED.new_id,
                confidence = GREATEST(legacy_id_mappings.confidence, EXCLUDED.confidence)
        $sql$;
    END IF;

    IF to_regclass('public.question_bank_levels') IS NOT NULL
       AND to_regclass('public.curriculum_levels') IS NOT NULL THEN
        EXECUTE $sql$
            WITH matched AS (
                SELECT DISTINCT ON (bank.tenant_id, bank.id)
                    bank.tenant_id,
                    bank.id AS legacy_id,
                    canonical.id AS new_id,
                    CASE
                        WHEN canonical.id = bank.id THEN 1.0000::numeric
                        ELSE 0.8000::numeric
                    END AS confidence
                FROM question_bank_levels bank
                JOIN legacy_id_mappings subject_map
                  ON subject_map.tenant_id = bank.tenant_id
                 AND subject_map.legacy_table = 'question_bank_subjects'
                 AND subject_map.legacy_id = bank.subject_id
                 AND subject_map.new_table = 'curriculum_subjects'
                JOIN curriculum_levels canonical
                  ON canonical.tenant_id = bank.tenant_id
                 AND canonical.subject_id = subject_map.new_id
                 AND (
                    canonical.id = bank.id
                    OR lower(canonical.code) = lower(BTRIM(bank.label))
                    OR lower(canonical.name) = lower(BTRIM(bank.label))
                 )
                WHERE bank.id IS NOT NULL
                  AND bank.tenant_id IS NOT NULL
                  AND NULLIF(BTRIM(bank.label), '') IS NOT NULL
                ORDER BY bank.tenant_id, bank.id,
                    CASE WHEN canonical.id = bank.id THEN 1 ELSE 2 END
            )
            INSERT INTO legacy_id_mappings (
                id, tenant_id, legacy_table, legacy_id, new_table, new_id, confidence, created_at
            )
            SELECT
                left(md5(tenant_id || ':question_bank_levels:' || legacy_id || ':curriculum_levels'), 32),
                tenant_id,
                'question_bank_levels',
                legacy_id,
                'curriculum_levels',
                new_id,
                confidence,
                now()
            FROM matched
            ON CONFLICT (tenant_id, legacy_table, legacy_id, new_table) DO UPDATE SET
                new_id = EXCLUDED.new_id,
                confidence = GREATEST(legacy_id_mappings.confidence, EXCLUDED.confidence)
        $sql$;
    END IF;

    IF to_regclass('public.question_bank_categories') IS NOT NULL
       AND to_regclass('public.curriculum_topics') IS NOT NULL THEN
        EXECUTE $sql$
            WITH matched AS (
                SELECT DISTINCT ON (bank.tenant_id, bank.id)
                    bank.tenant_id,
                    bank.id AS legacy_id,
                    canonical.id AS new_id,
                    CASE
                        WHEN canonical.id = bank.id THEN 1.0000::numeric
                        ELSE 0.8000::numeric
                    END AS confidence
                FROM question_bank_categories bank
                JOIN legacy_id_mappings level_map
                  ON level_map.tenant_id = bank.tenant_id
                 AND level_map.legacy_table = 'question_bank_levels'
                 AND level_map.legacy_id = bank.level_id
                 AND level_map.new_table = 'curriculum_levels'
                JOIN curriculum_topics canonical
                  ON canonical.tenant_id = bank.tenant_id
                 AND canonical.level_id = level_map.new_id
                 AND (
                    canonical.id = bank.id
                    OR lower(canonical.code) = lower(BTRIM(bank.label))
                    OR lower(canonical.name) = lower(BTRIM(bank.label))
                 )
                WHERE bank.id IS NOT NULL
                  AND bank.tenant_id IS NOT NULL
                  AND NULLIF(BTRIM(bank.label), '') IS NOT NULL
                ORDER BY bank.tenant_id, bank.id,
                    CASE WHEN canonical.id = bank.id THEN 1 ELSE 2 END
            )
            INSERT INTO legacy_id_mappings (
                id, tenant_id, legacy_table, legacy_id, new_table, new_id, confidence, created_at
            )
            SELECT
                left(md5(tenant_id || ':question_bank_categories:' || legacy_id || ':curriculum_topics'), 32),
                tenant_id,
                'question_bank_categories',
                legacy_id,
                'curriculum_topics',
                new_id,
                confidence,
                now()
            FROM matched
            ON CONFLICT (tenant_id, legacy_table, legacy_id, new_table) DO UPDATE SET
                new_id = EXCLUDED.new_id,
                confidence = GREATEST(legacy_id_mappings.confidence, EXCLUDED.confidence)
        $sql$;
    END IF;
END $$;
