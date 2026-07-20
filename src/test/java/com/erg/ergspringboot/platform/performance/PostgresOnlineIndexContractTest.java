package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PostgresOnlineIndexContractTest {

    @Test
    void measuredActivityCursorIndexIsDeployedOutsideFlywayWithoutBlockingWrites() throws Exception {
        String sql = Files.readString(Path.of(
                "ops/postgres/online/2026-07-16-user-activity-tenant-cursor.sql")).toLowerCase();

        assertThat(sql).contains(
                "create index concurrently if not exists idx_user_activities_tenant_created_id",
                "on user_activities(tenant_id, created_at desc, id desc)");
        assertThat(sql).doesNotContain("begin;", "commit;");
    }

    @Test
    void measuredRecipientUserLookupIndexAvoidsTenantWideRecipientFiltering() throws Exception {
        String sql = Files.readString(Path.of(
                "ops/postgres/online/2026-07-16-assignment-recipient-user-lookup.sql")).toLowerCase();

        assertThat(sql).contains(
                "create index concurrently if not exists idx_assignment_recipients_tenant_user_assignment",
                "on assignment_recipients(tenant_id, student_user_id, assignment_id)");
        assertThat(sql).doesNotContain("begin;", "commit;");
    }

    @Test
    void measuredQuizVersionAnalyticsIndexAvoidsFullAttemptScan() throws Exception {
        String sql = Files.readString(Path.of(
                "ops/postgres/online/2026-07-17-attempt-quiz-version-status.sql")).toLowerCase();

        assertThat(sql).contains(
                "create index concurrently if not exists idx_lms_attempts_tenant_version_status_id",
                "on lms_attempts(tenant_id, quiz_version_id, status, id)");
        assertThat(sql).doesNotContain("begin;", "commit;");

        String repository = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/repository/quiz/runtime/AttemptAnswerRowRepository.java"))
                .toLowerCase();
        assertThat(repository).contains("and attempt.tenant_id = :tenantid");
    }
}
