package com.erg.ergspringboot.content.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContentLifecycleMigrationContractTest {

    @Test
    void migrationPinsLifecycleColumnsTrailRlsAndCanonicalAdminPermissions() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/postgres/V92__content_lifecycle_contract.sql"));

        assertThat(sql).contains("curriculum_subjects", "curriculum_levels", "curriculum_topics",
                "content_items", "lms_questions", "lms_quizzes");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS lifecycle_state",
                "ADD COLUMN IF NOT EXISTS record_version");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS content_lifecycle_transitions",
                "ALTER TABLE content_lifecycle_transitions FORCE ROW LEVEL SECURITY",
                "tenant_id, resource_kind, resource_id, idempotency_key");
        assertThat(sql).contains("'content.lifecycle.submit'", "'content.lifecycle.approve'",
                "'content.lifecycle.publish'", "'content.lifecycle.archive'",
                "'content.lifecycle.rollback'");
        assertThat(sql).doesNotContain("'lcms', 'tenant,school'");
    }
}
