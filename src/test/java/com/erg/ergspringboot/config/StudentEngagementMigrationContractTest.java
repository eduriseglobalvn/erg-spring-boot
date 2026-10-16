package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StudentEngagementMigrationContractTest {

    @Test
    void migrationCreatesTenantIsolatedEngagementTablesAndRetryKeys() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/postgres/V83__student_engagement_contract.sql"
        ));

        assertThat(migration)
                .contains("CREATE TABLE IF NOT EXISTS lms_announcements")
                .contains("CREATE TABLE IF NOT EXISTS student_discussion_threads")
                .contains("CREATE TABLE IF NOT EXISTS student_discussion_comments")
                .contains("CREATE TABLE IF NOT EXISTS student_discussion_reactions")
                .contains("CREATE TABLE IF NOT EXISTS student_announcement_reads")
                .contains("uq_student_discussion_thread_idempotency")
                .contains("uq_student_discussion_comment_idempotency")
                .contains("ENABLE ROW LEVEL SECURITY")
                .contains("student_announcement_reads_tenant_isolation");
    }
}
