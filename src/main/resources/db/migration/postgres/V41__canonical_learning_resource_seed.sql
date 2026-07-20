-- Seed a minimal canonical curriculum/content graph for learning-resource screens.
-- Runtime reads this through GraphQL; no legacy resource tables or endpoints are used.

INSERT INTO curriculum_subjects (
    id, tenant_id, code, name, description, status, sort_order, created_by, updated_by, created_at, updated_at
)
VALUES
    ('sub_ic3', 'erg', 'ic3-digital-literacy', 'IC3 Digital Literacy', 'Digital literacy curriculum seed', 'active', 10, 'seed', 'seed', now(), now()),
    ('sub_tin_hoc', 'erg', 'tin-hoc', 'Tin hoc', 'Computer science curriculum seed', 'active', 20, 'seed', 'seed', now(), now())
ON CONFLICT ON CONSTRAINT uq_curriculum_subjects_tenant_code DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order,
    updated_at = now();

INSERT INTO curriculum_levels (
    id, tenant_id, subject_id, code, name, description, status, sort_order, created_by, updated_by, created_at, updated_at
)
VALUES
    ('lvl_ic3_gs6_l1', 'erg', 'sub_ic3', 'ic3-gs6-level-1', 'IC3 GS6 Level 1', 'Foundational IC3 level', 'active', 10, 'seed', 'seed', now(), now()),
    ('lvl_tin_hoc_6', 'erg', 'sub_tin_hoc', 'tin-hoc-6', 'Tin hoc 6', 'Grade 6 computer science', 'active', 20, 'seed', 'seed', now(), now())
ON CONFLICT ON CONSTRAINT uq_curriculum_levels_tenant_subject_code DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order,
    updated_at = now();

INSERT INTO curriculum_topics (
    id, tenant_id, level_id, code, name, description, status, sort_order, created_by, updated_by, created_at, updated_at
)
VALUES
    ('top_ic3_intro', 'erg', 'lvl_ic3_gs6_l1', 'intro-computing', 'Intro to Computing', 'Hardware, software, and operating system basics', 'active', 10, 'seed', 'seed', now(), now()),
    ('top_ic3_safety', 'erg', 'lvl_ic3_gs6_l1', 'internet-safety', 'Internet Safety', 'Safe digital citizenship and online communication', 'active', 20, 'seed', 'seed', now(), now()),
    ('top_tin_hoc_algo', 'erg', 'lvl_tin_hoc_6', 'algorithm-basics', 'Algorithm Basics', 'Step-by-step thinking and simple algorithms', 'active', 30, 'seed', 'seed', now(), now())
ON CONFLICT ON CONSTRAINT uq_curriculum_topics_tenant_level_code DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order,
    updated_at = now();

INSERT INTO content_items (
    id, tenant_id, content_type, title, description, status, visibility, owner_id, created_by, updated_by, created_at, updated_at
)
VALUES
    ('cnt_ic3_intro_deck', 'erg', 'LECTURE', 'IC3 Intro to Computing Deck', 'Seed lecture for the IC3 introduction topic', 'published', 'public', 'seed', 'seed', 'seed', now(), now()),
    ('cnt_ic3_safety_quiz', 'erg', 'EXERCISE', 'Internet Safety Practice', 'Seed exercise for safe internet behavior', 'published', 'public', 'seed', 'seed', 'seed', now(), now()),
    ('cnt_tin_hoc_algo_pdf', 'erg', 'LECTURE', 'Algorithm Basics Worksheet', 'Seed worksheet for algorithm basics', 'published', 'public', 'seed', 'seed', 'seed', now(), now())
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    visibility = EXCLUDED.visibility,
    updated_at = now();

INSERT INTO content_assets (
    id, tenant_id, asset_type, title, storage_provider, storage_url, mime_type, status, metadata, created_by, updated_by, created_at, updated_at
)
VALUES
    (
        'ast_ic3_intro_deck',
        'erg',
        'GOOGLE_SLIDE',
        'IC3 Intro to Computing Deck',
        'metadata_only',
        NULL,
        'application/vnd.google-apps.presentation',
        'active',
        '{"selectedFileType":"PPTX","launchMode":"google_slide_embed","canDownload":false}'::jsonb,
        'seed',
        'seed',
        now(),
        now()
    ),
    (
        'ast_tin_hoc_algo_pdf',
        'erg',
        'DOCUMENT',
        'Algorithm Basics Worksheet',
        'metadata_only',
        NULL,
        'application/pdf',
        'active',
        '{"selectedFileType":"PDF","launchMode":"pdf_reader","canDownload":true}'::jsonb,
        'seed',
        'seed',
        now(),
        now()
    )
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    asset_type = EXCLUDED.asset_type,
    status = EXCLUDED.status,
    metadata = EXCLUDED.metadata,
    updated_at = now();

INSERT INTO content_item_assets (
    id, tenant_id, content_item_id, content_asset_id, asset_role, sort_order, metadata, created_by, created_at
)
VALUES
    ('cia_ic3_intro_deck', 'erg', 'cnt_ic3_intro_deck', 'ast_ic3_intro_deck', 'primary', 1, '{"source":"canonical-seed"}'::jsonb, 'seed', now()),
    ('cia_tin_hoc_algo_pdf', 'erg', 'cnt_tin_hoc_algo_pdf', 'ast_tin_hoc_algo_pdf', 'primary', 1, '{"source":"canonical-seed"}'::jsonb, 'seed', now())
ON CONFLICT ON CONSTRAINT uq_content_item_assets_role DO UPDATE SET
    sort_order = EXCLUDED.sort_order,
    metadata = EXCLUDED.metadata;

INSERT INTO topic_content_items (
    id, tenant_id, topic_id, content_item_id, content_role, sort_order, status, created_by, created_at
)
VALUES
    ('tci_ic3_intro_deck', 'erg', 'top_ic3_intro', 'cnt_ic3_intro_deck', 'LECTURE', 1, 'active', 'seed', now()),
    ('tci_ic3_safety_quiz', 'erg', 'top_ic3_safety', 'cnt_ic3_safety_quiz', 'EXERCISE', 1, 'active', 'seed', now()),
    ('tci_tin_hoc_algo_pdf', 'erg', 'top_tin_hoc_algo', 'cnt_tin_hoc_algo_pdf', 'LECTURE', 1, 'active', 'seed', now())
ON CONFLICT ON CONSTRAINT uq_topic_content_items_item DO UPDATE SET
    sort_order = EXCLUDED.sort_order,
    status = EXCLUDED.status;
