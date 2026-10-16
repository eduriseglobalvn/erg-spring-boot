package com.erg.ergspringboot.service.lms;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CommandIdempotencyMigrationContractTest {

    @Test
    void assignmentAndQuizPublishKeysAreUniqueWithinTheirBusinessScope() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/postgres/V76__command_idempotency_uniqueness.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }

        assertThat(sql)
                .contains("unique index uq_lms_assignments_tenant_idempotency")
                .contains("on lms_assignments(tenant_id, idempotency_key)")
                .contains("unique index uq_quiz_versions_publish_idempotency")
                .contains("on quiz_versions(tenant_id, quiz_id, idempotency_key)")
                .contains("unique index uq_lms_quizzes_tenant_idempotency")
                .contains("on lms_quizzes(tenant_id, idempotency_key)")
                .contains("row_number() over");
    }
}
