-- Strict tenant predicate for the quiz-domain tables. Internal workers must use the
-- explicit TenantContext.maintenance() context; an unset tenant GUC no longer opens access.
do $$
declare
    protected_table text;
    protected_tables text[] := array[
        'quiz_drafts', 'question_bank_subjects', 'question_bank_levels',
        'question_bank_categories', 'quiz_bank_items', 'media_assets',
        'media_references', 'quiz_versions', 'quiz_version_sections',
        'quiz_version_questions', 'quiz_packages', 'attempt_events',
        'attempt_answers', 'student_progress', 'assignment_resources',
        'assignment_recipients', 'student_groups', 'student_group_members'
    ];
begin
    foreach protected_table in array protected_tables loop
        if exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = protected_table) then
            execute format('alter table %I enable row level security', protected_table);
            execute format('alter table %I force row level security', protected_table);
            execute format('drop policy if exists %I on %I', protected_table || '_tenant_isolation', protected_table);
            execute format($policy$
                create policy %I on %I
                using (
                    current_setting('app.maintenance_mode', true) = 'true'
                    or (nullif(current_setting('app.tenant_id', true), '') is not null
                        and tenant_id = current_setting('app.tenant_id', true))
                )
                with check (
                    current_setting('app.maintenance_mode', true) = 'true'
                    or (nullif(current_setting('app.tenant_id', true), '') is not null
                        and tenant_id = current_setting('app.tenant_id', true))
                )
            $policy$, protected_table || '_tenant_isolation', protected_table);
        end if;
    end loop;
end $$;
