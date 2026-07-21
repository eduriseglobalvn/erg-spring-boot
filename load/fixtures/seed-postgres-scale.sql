-- Deterministic, idempotent local benchmark fixture.
-- This script deliberately does not DROP or TRUNCATE any table.
-- Run through scripts/seed-local-postgres.ps1 after Flyway migration.

\set ON_ERROR_STOP on
BEGIN;
SET LOCAL synchronous_commit = off;

-- 450 teachers, 50 admins, and 4,500 students.
INSERT INTO users (id, created_at, updated_at, email, password_hash, full_name, status,
                   provider, account_type, tenant_id, username, is_profile_completed,
                   must_change_password, recovery_email_verified, authz_version)
SELECT format('seed-teacher-%s', lpad(n::text, 5, '0')), now(), now(),
       format('teacher%05s@seed.local', n), 'benchmark-password-hash',
       format('Seed Teacher %s', n), 'ACTIVE', 'LOCAL', 'TEACHER',
       format('seed-tenant-%s', lpad((((n - 1) / 45) + 1)::text, 2, '0')),
       format('seed-teacher-%s', lpad(n::text, 5, '0')), true, false, true, 1
FROM generate_series(1, 450) AS g(n)
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, created_at, updated_at, email, password_hash, full_name, status,
                   provider, account_type, tenant_id, username, is_profile_completed,
                   must_change_password, recovery_email_verified, authz_version)
SELECT format('seed-admin-%s', lpad(n::text, 5, '0')), now(), now(),
       format('admin%05s@seed.local', n), 'benchmark-password-hash',
       format('Seed Admin %s', n), 'ACTIVE', 'LOCAL', 'ADMIN',
       format('seed-tenant-%s', lpad((((n - 1) / 5) + 1)::text, 2, '0')),
       format('seed-admin-%s', lpad(n::text, 5, '0')), true, false, true, 1
FROM generate_series(1, 50) AS g(n)
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, created_at, updated_at, email, password_hash, full_name, status,
                   provider, account_type, tenant_id, username, is_profile_completed,
                   must_change_password, recovery_email_verified, authz_version)
SELECT format('seed-student-%s', lpad(n::text, 5, '0')), now(), now(),
       format('student%05s@seed.local', n), 'benchmark-password-hash',
       format('Seed Student %s', n), 'ACTIVE', 'LOCAL', 'STUDENT',
       format('seed-tenant-%s', lpad((((n - 1) / 450) + 1)::text, 2, '0')),
       format('seed-student-%s', lpad(n::text, 5, '0')), true, false, true, 1
FROM generate_series(1, 4500) AS g(n)
ON CONFLICT (id) DO NOTHING;

-- Predictable local-only bearer tokens: erg-load-<user-id>. Never use these outside this fixture.
INSERT INTO user_sessions
    (id, created_at, updated_at, user_id, session_id, device_id, device_name, platform,
     device_fingerprint, portal, ip_address, user_agent, access_token_hash, tenant_id,
     last_active_at, expires_at)
SELECT 'load-session-' || substr(md5(id), 1, 23), now(), now(), id,
       'load-' || id, 'k6-' || id, 'k6 local fixture', 'WEB_DESKTOP',
       md5('k6:' || id), 'lms', '127.0.0.1', 'k6-local-fixture',
       rtrim(translate(encode(sha256(convert_to('erg-load-' || id, 'UTF8')), 'base64'), '+/', '-_'), '='),
       tenant_id, now(), now() + interval '30 days'
FROM users
WHERE id LIKE 'seed-student-%' OR id LIKE 'seed-teacher-%' OR id LIKE 'seed-admin-%'
ON CONFLICT (id) DO UPDATE SET expires_at = EXCLUDED.expires_at, revoked_at = NULL, revoked_reason = NULL;

INSERT INTO user_roles (user_id, role_id)
SELECT id,
       CASE account_type WHEN 'STUDENT' THEN 'role_lms_student'
            WHEN 'TEACHER' THEN 'role_lms_teacher' ELSE 'role_super_admin' END
FROM users
WHERE id LIKE 'seed-student-%' OR id LIKE 'seed-teacher-%' OR id LIKE 'seed-admin-%'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- 10 tenant-owned schools. The first teacher in each tenant is the owner.
INSERT INTO education_units (id, created_at, updated_at, name, slug, type, status,
                             owner_user_id, created_by, source, tenant_id, version)
SELECT format('seed-school-%s', lpad(t::text, 2, '0')), now(), now(),
       format('Seed School %s', t), format('seed-school-%s', t), 'SCHOOL', 'ACTIVE',
       format('seed-teacher-%s', lpad(((t - 1) * 45 + 1)::text, 5, '0')),
       format('seed-teacher-%s', lpad(((t - 1) * 45 + 1)::text, 5, '0')),
       'LOCAL_BENCHMARK', format('seed-tenant-%s', lpad(t::text, 2, '0')), 0
FROM generate_series(1, 10) AS g(t)
ON CONFLICT (id) DO NOTHING;

-- Complete school-operation read models: two years and twelve grades per school.
INSERT INTO school_academic_years
    (id, tenant_id, school_id, start_year, end_year, status, starts_on, ends_on,
     created_by, updated_by, created_at, updated_at)
SELECT format('seed-school-year-%s-%s', lpad(t::text, 2, '0'), y),
       format('seed-tenant-%s', lpad(t::text, 2, '0')),
       format('seed-school-%s', lpad(t::text, 2, '0')),
       y, y + 1, CASE WHEN y = 2026 THEN 'CURRENT' ELSE 'CLOSED' END,
       make_date(y, 8, 15), make_date(y + 1, 5, 31),
       format('seed-teacher-%s', lpad(((t - 1) * 45 + 1)::text, 5, '0')),
       format('seed-teacher-%s', lpad(((t - 1) * 45 + 1)::text, 5, '0')), now(), now()
FROM generate_series(1, 10) AS tenant(t)
CROSS JOIN (VALUES (2025), (2026)) AS academic_year(y)
ON CONFLICT (id) DO NOTHING;

INSERT INTO school_operational_profiles
    (school_id, tenant_id, school_type, address, district, principal_name,
     contact_phone, contact_email, onboarding_status, current_academic_year_id,
     created_by, updated_by, created_at, updated_at)
SELECT format('seed-school-%s', lpad(t::text, 2, '0')),
       format('seed-tenant-%s', lpad(t::text, 2, '0')), 'SECONDARY',
       format('%s Nguyen Van Linh, Ho Chi Minh City', t), 'District 7',
       format('Seed Principal %s', t), format('090000%04s', t),
       format('school%02s@seed.local', t), 'ACTIVE',
       format('seed-school-year-%s-2026', lpad(t::text, 2, '0')),
       format('seed-teacher-%s', lpad(((t - 1) * 45 + 1)::text, 5, '0')),
       format('seed-teacher-%s', lpad(((t - 1) * 45 + 1)::text, 5, '0')), now(), now()
FROM generate_series(1, 10) AS tenant(t)
ON CONFLICT (school_id) DO UPDATE
SET current_academic_year_id = EXCLUDED.current_academic_year_id,
    onboarding_status = EXCLUDED.onboarding_status,
    updated_at = EXCLUDED.updated_at;

INSERT INTO school_grades
    (id, tenant_id, school_id, academic_year_id, grade_code, label, sort_order,
     status, source, created_at, updated_at)
SELECT format('seed-school-grade-%s-%s', lpad(t::text, 2, '0'), grade),
       format('seed-tenant-%s', lpad(t::text, 2, '0')),
       format('seed-school-%s', lpad(t::text, 2, '0')),
       format('seed-school-year-%s-2026', lpad(t::text, 2, '0')),
       grade::text, format('Grade %s', grade), grade, 'ACTIVE', 'DEFAULT', now(), now()
FROM generate_series(1, 10) AS tenant(t)
CROSS JOIN generate_series(1, 12) AS grades(grade)
ON CONFLICT (id) DO NOTHING;

-- Twenty legacy calendar subjects/levels: two per tenant.
INSERT INTO lms_subjects (id, created_at, updated_at, tenant_id, scope, name, code, status)
SELECT format('seed-lsub-%s-%s', lpad(t::text, 2, '0'), s), now(), now(),
       format('seed-tenant-%s', lpad(t::text, 2, '0')),
       jsonb_build_object('schoolId', format('seed-school-%s', lpad(t::text, 2, '0'))),
       format('Seed Subject %s.%s', t, s), format('SEED-%s-%s', t, s), 'ACTIVE'
FROM generate_series(1, 10) AS tenant(t)
CROSS JOIN generate_series(1, 2) AS subject(s)
ON CONFLICT (id) DO UPDATE SET scope = EXCLUDED.scope, updated_at = EXCLUDED.updated_at;

INSERT INTO lms_levels (id, created_at, updated_at, tenant_id, subject_id, name, code, sort_order, status)
SELECT format('seed-llevel-%s-%s', lpad(t::text, 2, '0'), s), now(), now(),
       format('seed-tenant-%s', lpad(t::text, 2, '0')),
       format('seed-lsub-%s-%s', lpad(t::text, 2, '0'), s),
       format('Seed Level %s.%s', t, s), format('SEED-L-%s-%s', t, s), s, 'ACTIVE'
FROM generate_series(1, 10) AS tenant(t)
CROSS JOIN generate_series(1, 2) AS subject(s)
ON CONFLICT (id) DO NOTHING;

-- Canonical taxonomy for question-bank and learning-resource workloads.
INSERT INTO curriculum_subjects
    (id, tenant_id, code, name, status, sort_order, created_by, updated_by, created_at, updated_at)
SELECT format('seed-csub-%s-%s', lpad(t::text, 2, '0'), s),
       format('seed-tenant-%s', lpad(t::text, 2, '0')), format('CSEED-%s-%s', t, s),
       format('Seed Canonical Subject %s.%s', t, s), 'active', s,
       format('seed-teacher-%s', lpad(((t - 1) * 45 + 1)::text, 5, '0')),
       format('seed-teacher-%s', lpad(((t - 1) * 45 + 1)::text, 5, '0')), now(), now()
FROM generate_series(1, 10) AS tenant(t)
CROSS JOIN generate_series(1, 2) AS subject(s)
ON CONFLICT (id) DO NOTHING;

INSERT INTO curriculum_levels
    (id, tenant_id, subject_id, code, name, status, sort_order, created_by, updated_by, created_at, updated_at)
SELECT format('seed-clevel-%s-%s', lpad(t::text, 2, '0'), s),
       format('seed-tenant-%s', lpad(t::text, 2, '0')),
       format('seed-csub-%s-%s', lpad(t::text, 2, '0'), s), format('CLSEED-%s-%s', t, s),
       format('Seed Canonical Level %s.%s', t, s), 'active', s,
       format('seed-teacher-%s', lpad(((t - 1) * 45 + 1)::text, 5, '0')),
       format('seed-teacher-%s', lpad(((t - 1) * 45 + 1)::text, 5, '0')), now(), now()
FROM generate_series(1, 10) AS tenant(t)
CROSS JOIN generate_series(1, 2) AS subject(s)
ON CONFLICT (id) DO NOTHING;

INSERT INTO curriculum_topics
    (id, tenant_id, level_id, code, name, status, sort_order, created_by, updated_by, created_at, updated_at)
SELECT format('seed-ctopic-%s-%s', lpad(t::text, 2, '0'), s),
       format('seed-tenant-%s', lpad(t::text, 2, '0')),
       format('seed-clevel-%s-%s', lpad(t::text, 2, '0'), s), format('CTSEED-%s-%s', t, s),
       format('Seed Canonical Topic %s.%s', t, s), 'active', s,
       format('seed-teacher-%s', lpad(((t - 1) * 45 + 1)::text, 5, '0')),
       format('seed-teacher-%s', lpad(((t - 1) * 45 + 1)::text, 5, '0')), now(), now()
FROM generate_series(1, 10) AS tenant(t)
CROSS JOIN generate_series(1, 2) AS subject(s)
ON CONFLICT (id) DO NOTHING;

-- 250 classes, 18 students per class. The final 12 students remain unassigned.
INSERT INTO academic_classes (id, created_at, updated_at, tenant_id, school_id, name,
                              grade, academic_year, status, homeroom_teacher_id)
SELECT format('seed-class-%s', lpad(n::text, 4, '0')), now(), now(),
       format('seed-tenant-%s', lpad((((n - 1) / 25) + 1)::text, 2, '0')),
       format('seed-school-%s', lpad((((n - 1) / 25) + 1)::text, 2, '0')),
       format('Seed Class %s', n), ((n - 1) % 12 + 1)::text, '2026-2027', 'ACTIVE',
       format('seed-teacher-%s', lpad((((n - 1) % 450 + 1)::text), 5, '0'))
FROM generate_series(1, 250) AS g(n)
ON CONFLICT (id) DO NOTHING;

INSERT INTO lms_students (id, created_at, updated_at, tenant_id, school_id,
                          academic_class_id, student_code, full_name, username,
                          auth_user_id, email, status, metrics)
SELECT format('seed-lms-student-%s', lpad(n::text, 5, '0')), now(), now(),
       format('seed-tenant-%s', lpad((((n - 1) / 450) + 1)::text, 2, '0')),
       format('seed-school-%s', lpad((((n - 1) / 450) + 1)::text, 2, '0')),
       format('seed-class-%s', lpad((((n - 1) / 18) + 1)::text, 4, '0')),
       format('SEED-%05s', n), format('Seed Student %s', n),
       format('seed-student-%s', lpad(n::text, 5, '0')),
       format('seed-student-%s', lpad(n::text, 5, '0')),
       format('student%05s@seed.local', n), 'ACTIVE', '{}'::jsonb
FROM generate_series(1, 4500) AS g(n)
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_access_scopes
    (id, created_at, updated_at, user_id, education_unit_id, modules, role)
SELECT 'load-scope-' || substr(md5(student.auth_user_id), 1, 25), now(), now(),
       student.auth_user_id, student.school_id, 'lms', 'student'
FROM lms_students student
WHERE student.id LIKE 'seed-lms-student-%'
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_role_assignments
    (id, user_id, role_id, tenant_id, scope_type, scope_id, assigned_by, created_at, updated_at)
SELECT 'load-class-' || substr(md5(student.auth_user_id), 1, 25), student.auth_user_id,
       'role_lms_student', student.tenant_id, 'CLASS', student.academic_class_id,
       format('seed-teacher-%s', lpad((((right(student.tenant_id, 2)::int - 1) * 45) + 1)::text, 5, '0')),
       now(), now()
FROM lms_students student
WHERE student.id LIKE 'seed-lms-student-%'
ON CONFLICT (id) DO NOTHING;

-- One 50-question quiz per tenant (500 tenant-isolated bank questions total).
INSERT INTO lms_questions
    (id, created_at, updated_at, tenant_id, scope, subject_id, level_id, topic_id,
     category_id, type, stem, choices, answer, metadata, status, difficulty, tags,
     owner_user_id, schema_version, settings)
SELECT format('seed-q-%s-%s', lpad(t::text, 2, '0'), lpad(q::text, 3, '0')), now(), now(),
       format('seed-tenant-%s', lpad(t::text, 2, '0')), '{}'::jsonb,
       format('seed-csub-%s-%s', lpad(t::text, 2, '0'), ((q - 1) % 2) + 1),
       format('seed-clevel-%s-%s', lpad(t::text, 2, '0'), ((q - 1) % 2) + 1),
       format('seed-ctopic-%s-%s', lpad(t::text, 2, '0'), ((q - 1) % 2) + 1),
       format('seed-ctopic-%s-%s', lpad(t::text, 2, '0'), ((q - 1) % 2) + 1),
       'multiple_choice', format('Seed question %s for tenant %s?', q, t),
       '[{"id":"a","text":"A"},{"id":"b","text":"B"}]'::jsonb,
       '{"choiceId":"a"}'::jsonb, '{}'::jsonb, 'ACTIVE', 'MEDIUM', '["seed"]'::jsonb,
       format('seed-teacher-%s', lpad(((t - 1) * 45 + 1)::text, 5, '0')), 1, '{}'::jsonb
FROM generate_series(1, 10) AS tenant(t)
CROSS JOIN generate_series(1, 50) AS question(q)
ON CONFLICT (id) DO NOTHING;

INSERT INTO lms_quizzes
    (id, created_at, updated_at, tenant_id, scope, title, kind, subject_id, level_id,
     topic_ids, question_ids, settings, status, version, portal, category_id,
     owner_user_id, content_scope, schema_version, idempotency_key)
SELECT format('seed-quiz-%s', lpad(t::text, 2, '0')), now(), now(),
       format('seed-tenant-%s', lpad(t::text, 2, '0')), '{}'::jsonb,
       format('Seed 50-question Quiz %s', t), 'ASSESSMENT',
       format('seed-csub-%s-1', lpad(t::text, 2, '0')),
       format('seed-clevel-%s-1', lpad(t::text, 2, '0')),
       jsonb_build_array(format('seed-ctopic-%s-1', lpad(t::text, 2, '0'))), '[]'::jsonb,
       '{"durationMinutes":60}'::jsonb, 'PUBLISHED', 1, 'LCMS',
       format('seed-ctopic-%s-1', lpad(t::text, 2, '0')),
       format('seed-teacher-%s', lpad(((t - 1) * 45 + 1)::text, 5, '0')),
       '{}'::jsonb, 1, format('seed-quiz-key-%s', t)
FROM generate_series(1, 10) AS tenant(t)
ON CONFLICT (id) DO NOTHING;

INSERT INTO lms_quiz_questions
    (id, tenant_id, quiz_id, question_id, order_index, source, points, settings, metadata)
SELECT 'seed-qq-' || substr(md5(t::text || ':' || q::text), 1, 26),
       format('seed-tenant-%s', lpad(t::text, 2, '0')),
       format('seed-quiz-%s', lpad(t::text, 2, '0')),
       format('seed-q-%s-%s', lpad(t::text, 2, '0'), lpad(q::text, 3, '0')),
       q - 1, 'seed', 1, '{}'::jsonb, '{}'::jsonb
FROM generate_series(1, 10) AS tenant(t)
CROSS JOIN generate_series(1, 50) AS question(q)
ON CONFLICT (tenant_id, quiz_id, question_id) DO NOTHING;

-- Published immutable versions used by quiz workspace/package/analytics reads.
INSERT INTO quiz_versions
    (id, created_at, updated_at, quiz_id, tenant_id, version_label, version_number,
     authoring_snapshot, runtime_quiz, answer_key, grading_mode, schema_version,
     content_hash, published_by, published_at, is_active, idempotency_key)
SELECT format('seed-qver-%s', lpad(t::text, 2, '0')), now(), now(),
       format('seed-quiz-%s', lpad(t::text, 2, '0')),
       format('seed-tenant-%s', lpad(t::text, 2, '0')), 'v1', 1,
       '{"source":"scale-fixture"}'::jsonb,
       jsonb_build_object('questionCount', 50, 'version', 1),
       '{"grading":"auto"}'::jsonb, 'server', 1,
       md5('seed-version-' || t::text),
       format('seed-teacher-%s', lpad(((t - 1) * 45 + 1)::text, 5, '0')),
       now() - interval '7 days', true, format('seed-version-key-%s', t)
FROM generate_series(1, 10) AS tenant(t)
ON CONFLICT (id) DO NOTHING;

INSERT INTO quiz_version_sections
    (id, created_at, updated_at, quiz_version_id, tenant_id, title, order_index)
SELECT format('seed-qsec-%s', lpad(t::text, 2, '0')), now(), now(),
       format('seed-qver-%s', lpad(t::text, 2, '0')),
       format('seed-tenant-%s', lpad(t::text, 2, '0')), 'Scale fixture section', 0
FROM generate_series(1, 10) AS tenant(t)
ON CONFLICT (id) DO NOTHING;

INSERT INTO quiz_version_questions
    (id, created_at, updated_at, quiz_version_id, section_id, tenant_id,
     runtime_question_id, source_slide_id, bank_question_id, kind, authoring_type,
     points, is_auto_gradable, grading_policy, order_index, body, answer_key)
SELECT 'seed-qvq-' || substr(md5(t::text || ':' || q::text), 1, 27), now(), now(),
       format('seed-qver-%s', lpad(t::text, 2, '0')),
       format('seed-qsec-%s', lpad(t::text, 2, '0')),
       format('seed-tenant-%s', lpad(t::text, 2, '0')),
       format('runtime-%s-%s', t, q), format('slide-%s-%s', t, q),
       format('seed-q-%s-%s', lpad(t::text, 2, '0'), lpad(q::text, 3, '0')),
       'multiple_choice', 'multiple_choice', 1, true, 'auto', q - 1,
       jsonb_build_object('stem', format('Seed question %s for tenant %s?', q, t)),
       '{"choiceId":"a"}'::jsonb
FROM generate_series(1, 10) AS tenant(t)
CROSS JOIN generate_series(1, 50) AS question(q)
ON CONFLICT (id) DO NOTHING;

UPDATE lms_quizzes quiz
SET current_version_id = format('seed-qver-%s', right(quiz.tenant_id, 2)), updated_at = now()
WHERE quiz.id LIKE 'seed-quiz-%' AND quiz.current_version_id IS NULL;

-- 1,000 canonical learning resources for library/list/cache benchmarks.
INSERT INTO content_items
    (id, tenant_id, content_type, title, description, status, visibility,
     owner_id, created_by, updated_by, created_at, updated_at)
SELECT format('seed-content-%s', lpad(n::text, 4, '0')),
       format('seed-tenant-%s', lpad((((n - 1) % 10) + 1)::text, 2, '0')),
       CASE WHEN n % 3 = 0 THEN 'video' WHEN n % 3 = 1 THEN 'document' ELSE 'lesson' END,
       format('Seed Learning Resource %s', n), 'Local 5k benchmark learning material',
       'published', 'tenant',
       format('seed-teacher-%s', lpad(((((n - 1) % 450) + 1))::text, 5, '0')),
       format('seed-teacher-%s', lpad(((((n - 1) % 450) + 1))::text, 5, '0')),
       format('seed-teacher-%s', lpad(((((n - 1) % 450) + 1))::text, 5, '0')),
       now() - (n % 365) * interval '1 day', now() - (n % 30) * interval '1 hour'
FROM generate_series(1, 1000) AS g(n)
ON CONFLICT (id) DO NOTHING;

-- Fifty topics per canonical level (1,000 total) and one placement per resource.
INSERT INTO curriculum_topics
    (id, tenant_id, level_id, code, name, status, sort_order, created_by, updated_by, created_at, updated_at)
SELECT format('seed-ctopic-%s-%s-%s', lpad(t::text, 2, '0'), s, lpad(q::text, 2, '0')),
       format('seed-tenant-%s', lpad(t::text, 2, '0')),
       format('seed-clevel-%s-%s', lpad(t::text, 2, '0'), s),
       format('CTSEED-%s-%s-%s', t, s, q), format('Seed Topic %s.%s.%s', t, s, q),
       'active', q,
       format('seed-teacher-%s', lpad(((t - 1) * 45 + 1)::text, 5, '0')),
       format('seed-teacher-%s', lpad(((t - 1) * 45 + 1)::text, 5, '0')), now(), now()
FROM generate_series(1, 10) AS tenant(t)
CROSS JOIN generate_series(1, 2) AS subject(s)
CROSS JOIN generate_series(1, 50) AS topic(q)
ON CONFLICT (id) DO NOTHING;

INSERT INTO topic_content_items
    (id, tenant_id, topic_id, content_item_id, content_role, sort_order, status, created_by, created_at, updated_at)
SELECT 'seed-place-' || lpad(n::text, 4, '0'),
       format('seed-tenant-%s', lpad((((n - 1) % 10) + 1)::text, 2, '0')),
       format('seed-ctopic-%s-%s-%s',
              lpad((((n - 1) % 10) + 1)::text, 2, '0'),
              (((n - 1) % 2) + 1), lpad((((n - 1) % 50) + 1)::text, 2, '0')),
       format('seed-content-%s', lpad(n::text, 4, '0')), 'primary', 1, 'active',
       format('seed-teacher-%s', lpad(((((n - 1) % 450) + 1))::text, 5, '0')), now(), now()
FROM generate_series(1, 1000) AS resource(n)
ON CONFLICT (id) DO NOTHING;

-- 40 assignments per class (10,000 total), with no external integrations.
INSERT INTO lms_assignments (id, created_at, updated_at, tenant_id, academic_class_id,
                             student_ids, due_at, teacher_note, status, assigned_by,
                             recipient_mode, attempt_limit, max_duration_minutes,
                             start_at, title, idempotency_key, recipient_count)
SELECT format('seed-assignment-%s-%s', lpad(c.n::text, 4, '0'), lpad(a.n::text, 2, '0')),
       now(), now(), format('seed-tenant-%s', lpad((((c.n - 1) / 25) + 1)::text, 2, '0')),
       format('seed-class-%s', lpad(c.n::text, 4, '0')), '[]'::jsonb,
       now() + interval '30 days', 'local benchmark assignment', 'PUBLISHED',
       format('seed-teacher-%s', lpad((((c.n - 1) % 450 + 1)::text), 5, '0')),
       'CLASS', 3, 60, now(), format('Seed Assignment %s-%s', c.n, a.n),
       format('seed-assignment-key-%s-%s', c.n, a.n), 18
FROM generate_series(1, 250) AS c(n)
CROSS JOIN generate_series(1, 40) AS a(n)
ON CONFLICT (id) DO NOTHING;

UPDATE lms_assignments
SET quiz_id = format('seed-quiz-%s', right(tenant_id, 2)), updated_at = now()
WHERE id LIKE 'seed-assignment-%' AND quiz_id IS NULL;

-- Set-based recipient fan-out: 180,000 rows, no per-row application loop.
INSERT INTO assignment_recipients (id, created_at, updated_at, assignment_id, tenant_id,
                                   student_id, student_user_id, class_id, source_mode)
SELECT 'rec-' || substr(md5(a.id || ':' || s.id), 1, 32), now(), now(), a.id, a.tenant_id, s.id, s.auth_user_id,
       a.academic_class_id, 'class'
FROM lms_assignments a
JOIN lms_students s ON s.academic_class_id = a.academic_class_id
WHERE a.id LIKE 'seed-assignment-%'
ON CONFLICT (id) DO NOTHING;

-- One submitted attempt per recipient for realistic progress queries.
INSERT INTO lms_attempts (id, tenant_id, assignment_id, student_id, status, answers,
                          score, max_score, percent, passed, started_at, submitted_at,
                          updated_at, class_id, mode, submitted_count, total_questions)
SELECT 'att-' || substr(md5(r.id), 1, 32), r.tenant_id, r.assignment_id, r.student_id,
       'SUBMITTED', '{}'::jsonb, 8, 10, 80, true, now() - interval '2 days',
       now() - interval '1 day', now(), r.class_id, 'ONLINE', 8, 10
FROM assignment_recipients r
WHERE r.id LIKE 'rec-%'
ON CONFLICT (id) DO NOTHING;

UPDATE lms_attempts attempt
SET quiz_version_id = format('seed-qver-%s', right(attempt.tenant_id, 2)),
    quiz_id = format('seed-quiz-%s', right(attempt.tenant_id, 2)), updated_at = now()
WHERE attempt.id LIKE 'att-%' AND attempt.quiz_version_id IS NULL;

-- Five normalized answer rows per submitted attempt (900,000 rows) for question analytics.
INSERT INTO attempt_answers
    (attempt_id, quiz_version_question_id, tenant_id, question_kind, raw_answer,
     correct, partially_right, awarded_points, max_points, graded_by, graded_at,
     needs_manual_grade)
SELECT attempt.id,
       'seed-qvq-' || substr(md5((right(attempt.tenant_id, 2)::int)::text || ':' || q::text), 1, 27),
       attempt.tenant_id, 'multiple_choice',
       jsonb_build_object('choiceId', CASE WHEN q % 5 = 0 THEN 'b' ELSE 'a' END),
       q % 5 <> 0, false, CASE WHEN q % 5 <> 0 THEN 1 ELSE 0 END, 1,
       'auto', attempt.submitted_at, false
FROM lms_attempts attempt
CROSS JOIN generate_series(1, 5) AS question(q)
WHERE attempt.id LIKE 'att-%'
ON CONFLICT (attempt_id, quiz_version_question_id) DO NOTHING;

-- Canonical one-row-per-student progress used by interactive and durable reports.
INSERT INTO student_progress
    (id, created_at, updated_at, tenant_id, student_user_id, student_name,
     quiz_id, quiz_title, assignment_id, class_id, mode, status,
     current_question_index, answered_count, total_questions, percent_complete,
     score, passed, started_at, submitted_at, last_active_at)
SELECT 'prog-' || substr(md5(r.id), 1, 31), now() - interval '2 days', now(),
       r.tenant_id, r.student_user_id, student.full_name,
       assignment.quiz_id, quiz.title, r.assignment_id, r.class_id, 'ONLINE', 'submitted',
       49, 50, 50, 100, 80, true, now() - interval '2 days',
       now() - interval '1 day', now() - interval '1 day'
FROM assignment_recipients r
JOIN lms_assignments assignment ON assignment.id = r.assignment_id
JOIN lms_quizzes quiz ON quiz.id = assignment.quiz_id
JOIN lms_students student ON student.id = r.student_id
WHERE r.id LIKE 'rec-%'
ON CONFLICT (tenant_id, student_user_id, quiz_id, assignment_id) DO NOTHING;

-- Exactly 5,000 pre-created attempts for the one-minute submission burst scenario.
INSERT INTO lms_attempts
    (id, tenant_id, assignment_id, quiz_id, student_id, package_hash, status, answers,
     score, max_score, percent, passed, started_at, updated_at, class_id, mode,
     submitted_count, total_questions, last_active_at)
SELECT format('load-attempt-%s-0', n), student.tenant_id,
       format('seed-assignment-%s-01', right(student.academic_class_id, 4)),
       format('seed-quiz-%s', right(student.tenant_id, 2)), student.id,
       'load-fixture', 'in_progress', '{}'::jsonb, 0, 50, 0, false,
       now() - interval '5 minutes', now(), student.academic_class_id, 'ONLINE', 0, 50, now()
FROM generate_series(1, 5000) AS load(n)
JOIN lms_students student
  ON student.id = format('seed-lms-student-%s', lpad((((n - 1) % 4500) + 1)::text, 5, '0'))
ON CONFLICT (id) DO NOTHING;

-- Ten published batches and 100,000 calendar rows across all tenants/classes.

-- Fifty representative report jobs across tenants and status states.
INSERT INTO report_jobs
    (id, tenant_id, requested_by, report_type, aggregate_id, idempotency_key,
     request_json, result_json, status, progress, attempts, available_at,
     created_at, started_at, completed_at)
SELECT format('seed-report-%s', lpad(n::text, 3, '0')),
       format('seed-tenant-%s', lpad((((n - 1) / 5) + 1)::text, 2, '0')),
       format('seed-admin-%s', lpad(n::text, 5, '0')), 'assignment-progress',
       format('seed-assignment-%s-01', lpad(((((n - 1) % 250) + 1))::text, 4, '0')),
       format('seed-report-key-%s', n), jsonb_build_object('fixture', true),
       CASE WHEN n % 2 = 0 THEN jsonb_build_object('ready', true) ELSE NULL END,
       CASE WHEN n % 2 = 0 THEN 'completed' ELSE 'queued' END,
       CASE WHEN n % 2 = 0 THEN 100 ELSE 0 END, CASE WHEN n % 2 = 0 THEN 1 ELSE 0 END,
       now(), now() - interval '1 day',
       CASE WHEN n % 2 = 0 THEN now() - interval '23 hours' ELSE NULL END,
       CASE WHEN n % 2 = 0 THEN now() - interval '22 hours' ELSE NULL END
FROM generate_series(1, 50) AS report(n)
ON CONFLICT (id) DO NOTHING;

INSERT INTO teaching_schedule_batches
    (id, tenant_id, school_id, academic_year, apply_from, repeat_weeks, status, source,
     idempotency_key, payload_hash, note, created_by, published_by, published_at)
SELECT format('seed-cal-batch-%s', lpad(t::text, 2, '0')),
       format('seed-tenant-%s', lpad(t::text, 2, '0')),
       format('seed-school-%s', lpad(t::text, 2, '0')), '2026-2027', current_date, 52,
       'PUBLISHED', 'LCMS_BULK_GRID', format('seed-calendar-%s', t), md5(t::text),
       'Local 5k benchmark schedule',
       format('seed-teacher-%s', lpad(((t - 1) * 45 + 1)::text, 5, '0')),
       format('seed-teacher-%s', lpad(((t - 1) * 45 + 1)::text, 5, '0')), now()
FROM generate_series(1, 10) AS tenant(t)
ON CONFLICT (id) DO NOTHING;

INSERT INTO teaching_schedule_events
    (id, tenant_id, batch_id, school_id, academic_class_id, subject_id, level_id,
     room_name, title, start_at, end_at, weekday, session, period_start, period_count,
     status, note, created_by, updated_by)
SELECT 'seed-cal-' || substr(md5(n::text), 1, 27),
       format('seed-tenant-%s', lpad(t::text, 2, '0')),
       format('seed-cal-batch-%s', lpad(t::text, 2, '0')),
       format('seed-school-%s', lpad(t::text, 2, '0')),
       format('seed-class-%s', lpad((((t - 1) * 25) + (((n - 1) / 10) % 25) + 1)::text, 4, '0')),
       format('seed-lsub-%s-%s', lpad(t::text, 2, '0'), ((n - 1) % 2) + 1),
       format('seed-llevel-%s-%s', lpad(t::text, 2, '0'), ((n - 1) % 2) + 1),
       format('Seed Room %s', ((n - 1) % 50) + 1), format('Seed Calendar Lesson %s', n),
       event_start, event_start + interval '45 minutes', extract(isodow from event_start)::smallint,
       CASE WHEN extract(hour from event_start) < 12 THEN 'MORNING' ELSE 'AFTERNOON' END,
       (((n - 1) % 8) + 1)::smallint, 1, 'PUBLISHED', 'local benchmark calendar',
       format('seed-teacher-%s', lpad(((t - 1) * 45 + ((n - 1) % 45) + 1)::text, 5, '0')),
       format('seed-teacher-%s', lpad(((t - 1) * 45 + ((n - 1) % 45) + 1)::text, 5, '0'))
FROM generate_series(1, 100000) AS event(n)
CROSS JOIN LATERAL (SELECT ((n - 1) % 10) + 1 AS t) tenant
CROSS JOIN LATERAL (SELECT now() + ((n - 1) % 365) * interval '1 day'
                           + (8 + ((n - 1) % 8)) * interval '1 hour' AS event_start) timing
ON CONFLICT (id) DO NOTHING;

-- 1,000,000 history rows for pagination/reporting benchmarks.
INSERT INTO user_activities (id, user_id, tenant_id, action, target_user_id,
                             ip_address, user_agent, description, created_at,
                             request_id, metadata)
SELECT format('seed-activity-%s', lpad(n::text, 7, '0')),
       format('seed-student-%s', lpad(((n - 1) % 4500 + 1)::text, 5, '0')),
       format('seed-tenant-%s', lpad((((((n - 1) % 4500) / 450) + 1)::text), 2, '0')),
       CASE WHEN n % 4 = 0 THEN 'LOGIN' WHEN n % 4 = 1 THEN 'VIEW_ASSIGNMENT'
            WHEN n % 4 = 2 THEN 'SUBMIT_ATTEMPT' ELSE 'OPEN_DASHBOARD' END,
       NULL, '127.0.0.1', 'erg-local-benchmark', 'local benchmark activity',
       now() - (n % 365) * interval '1 minute', format('seed-request-%s', n), '{}'::jsonb
FROM generate_series(1, 1000000) AS g(n)
ON CONFLICT (id) DO NOTHING;

-- Durable Modulith event benchmark commands. Each publish must hit a fresh DRAFT
-- batch; reusing one batch would measure only the idempotent 409 path.
INSERT INTO teaching_schedule_batches
    (id, tenant_id, school_id, academic_year, apply_from, repeat_weeks, status,
     source, note, created_by, created_at, updated_at)
SELECT format('load-batch-%s', lpad(n::text, 6, '0')), 'seed-tenant-01',
       'seed-school-01', '2026', DATE '2026-08-15', 1, 'DRAFT',
       'LOAD_BENCHMARK', 'modulith event benchmark', 'seed-admin-00001', now(), now()
FROM generate_series(1, 20000) AS g(n)
ON CONFLICT (id) DO UPDATE SET status = 'DRAFT', published_by = NULL,
    published_at = NULL, updated_at = now();

INSERT INTO teaching_schedule_events
    (id, tenant_id, batch_id, school_id, academic_class_id, subject_id,
     title, start_at, end_at, weekday, session, period_start, period_count,
     status, created_by, updated_by, created_at, updated_at)
SELECT format('load-batch-event-%s', lpad(n::text, 6, '0')), 'seed-tenant-01',
       format('load-batch-%s', lpad(n::text, 6, '0')), 'seed-school-01',
       'seed-class-0001', 'seed-lsub-01-1', 'load event',
       timestamptz '2026-08-17 08:00:00+07', timestamptz '2026-08-17 09:00:00+07',
       1, 'MORNING', 1, 1, 'DRAFT', 'seed-admin-00001', 'seed-admin-00001', now(), now()
FROM generate_series(1, 20000) AS g(n)
ON CONFLICT (id) DO UPDATE SET status = 'DRAFT', updated_by = 'seed-admin-00001', updated_at = now();

COMMIT;

\echo 'Local benchmark fixture counts:'
SELECT 'users' AS entity, count(*) FROM users WHERE id LIKE 'seed-%'
UNION ALL SELECT 'schools', count(*) FROM education_units WHERE id LIKE 'seed-school-%'
UNION ALL SELECT 'classes', count(*) FROM academic_classes WHERE id LIKE 'seed-class-%'
UNION ALL SELECT 'students', count(*) FROM lms_students WHERE id LIKE 'seed-lms-student-%'
UNION ALL SELECT 'assignments', count(*) FROM lms_assignments WHERE id LIKE 'seed-assignment-%'
UNION ALL SELECT 'recipients', count(*) FROM assignment_recipients WHERE id LIKE 'rec-%'
UNION ALL SELECT 'attempts', count(*) FROM lms_attempts WHERE id LIKE 'att-%'
UNION ALL SELECT 'questions', count(*) FROM lms_questions WHERE id LIKE 'seed-q-%'
UNION ALL SELECT 'quizzes', count(*) FROM lms_quizzes WHERE id LIKE 'seed-quiz-%'
UNION ALL SELECT 'quiz_questions', count(*) FROM lms_quiz_questions WHERE id LIKE 'seed-qq-%'
UNION ALL SELECT 'student_progress', count(*) FROM student_progress WHERE id LIKE 'prog-%'
UNION ALL SELECT 'load_sessions', count(*) FROM user_sessions WHERE id LIKE 'load-session-%'
UNION ALL SELECT 'burst_attempts', count(*) FROM lms_attempts WHERE id LIKE 'load-attempt-%'
UNION ALL SELECT 'learning_resources', count(*) FROM content_items WHERE id LIKE 'seed-content-%'
UNION ALL SELECT 'calendar_events', count(*) FROM teaching_schedule_events WHERE id LIKE 'seed-cal-%'
UNION ALL SELECT 'event_benchmark_batches', count(*) FROM teaching_schedule_batches WHERE id LIKE 'load-batch-%'
UNION ALL SELECT 'activities', count(*) FROM user_activities WHERE id LIKE 'seed-activity-%'
ORDER BY entity;
