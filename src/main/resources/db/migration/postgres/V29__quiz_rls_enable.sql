-- V29__quiz_rls_enable.sql
-- Phase 6 of the Quiz backend (docs/0706/quiz-backend-plan.md §9 RLS hardening).
-- Turns on row-level security for the quiz-domain tenant tables introduced in Phases 0-5. The GUC
-- plumbing (app.tenant_id) was laid in Phase 0.4 (TenantContext/TenantGucApplier) and is wired into
-- every request transaction as of this phase.
--
-- SAFETY-FIRST POLICY DESIGN (deliberate):
--  * Policies use FORCE ROW LEVEL SECURITY so the table owner is also constrained.
--  * The USING/CHECK predicate is:  current_setting('app.tenant_id', true) IS NULL
--                                   OR tenant_id = current_setting('app.tenant_id', true)
--    V65 tightens this compatibility policy after all internal workers gain an explicit maintenance
--    context. The historical migration remains unchanged so existing databases can upgrade safely.
--    Once TenantGucApplier sets app.tenant_id for a request, that request is hard-scoped to its tenant.
--  * This is the low-risk enforcing mode: it guarantees no cross-tenant read/write for tenant-scoped
--    requests, without risking a lockout of the maintenance paths that legitimately run unscoped.
--
-- Applied only to the NEW quiz-domain tables (this initiative owns them). Pre-existing lms_* tables are
-- left untouched here to avoid destabilizing running modules; extending RLS to them is a separate change.

-- Helper predicate is inlined per policy (PostgreSQL has no shared policy expression). tenant_id is
-- varchar on every table below, matching current_setting()'s text return.

DO $$
DECLARE
    quiz_table text;
    quiz_tables text[] := ARRAY[
        'quiz_drafts',
        'question_bank_subjects',
        'question_bank_levels',
        'question_bank_categories',
        'quiz_bank_items',
        'media_assets',
        'media_references',
        'quiz_versions',
        'quiz_version_sections',
        'quiz_version_questions',
        'quiz_packages',
        'attempt_events',
        'attempt_answers',
        'student_progress',
        'assignment_resources',
        'assignment_recipients',
        'student_groups',
        'student_group_members'
    ];
BEGIN
    FOREACH quiz_table IN ARRAY quiz_tables LOOP
        -- Only act on tables that exist (all created in V23-V28; guard keeps this re-runnable).
        IF EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = quiz_table
        ) THEN
            EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', quiz_table);
            EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', quiz_table);
            EXECUTE format('DROP POLICY IF EXISTS %I ON %I', quiz_table || '_tenant_isolation', quiz_table);
            EXECUTE format($f$
                CREATE POLICY %I ON %I
                USING (
                    current_setting('app.tenant_id', true) IS NULL
                    OR current_setting('app.tenant_id', true) = ''
                    OR tenant_id = current_setting('app.tenant_id', true)
                )
                WITH CHECK (
                    current_setting('app.tenant_id', true) IS NULL
                    OR current_setting('app.tenant_id', true) = ''
                    OR tenant_id = current_setting('app.tenant_id', true)
                )
            $f$, quiz_table || '_tenant_isolation', quiz_table);
        END IF;
    END LOOP;
END $$;
