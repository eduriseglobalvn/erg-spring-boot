-- Keep demo runtime data valid for fresh and existing databases without changing an applied migration.

UPDATE lms_assignments
SET due_at = CURRENT_TIMESTAMP + INTERVAL '7 days',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'asg_ba_math_g6_001'
  AND tenant_id = 'erg'
  AND due_at <= CURRENT_TIMESTAMP;

INSERT INTO quiz_versions (
    id, created_at, updated_at, quiz_id, tenant_id, version_label, version_number,
    authoring_snapshot, runtime_quiz, answer_key, grading_mode, schema_version,
    content_hash, published_by, published_at, is_active, idempotency_key
)
SELECT 'qv_math_g6_v1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'qz_math_g6_base', 'erg',
       'v1', 1, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, 'client-first', 1,
       'pkg_math_g6_v1', 'usr_teacher_ba', CURRENT_TIMESTAMP, true, 'demo-qz-math-g6-v1'
WHERE NOT EXISTS (SELECT 1 FROM quiz_versions WHERE id = 'qv_math_g6_v1');

INSERT INTO quiz_packages (
    id, created_at, updated_at, quiz_id, quiz_version_id, quiz_version, tenant_id,
    content_hash, signature, public_key_id, grading_mode, schema_version,
    package_format, assets_manifest, package_json, generated_at, expires_at
)
SELECT 'pkg_math_g6', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'qz_math_g6_base', 'qv_math_g6_v1',
       '1', 'erg', 'pkg_math_g6_v1', null, null, 'client-first', 1, 'single',
       '{}'::jsonb, '{}'::jsonb, CURRENT_TIMESTAMP, null
WHERE NOT EXISTS (SELECT 1 FROM quiz_packages WHERE id = 'pkg_math_g6');
