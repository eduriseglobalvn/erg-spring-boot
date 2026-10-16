package com.erg.ergspringboot.learningoutcomes;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.learningoutcomes.authorization.LearningOutcomesIssueCertificate;
import com.erg.ergspringboot.learningoutcomes.authorization.LearningOutcomesRead;
import com.erg.ergspringboot.learningoutcomes.authorization.LearningOutcomesWrite;
import com.erg.ergspringboot.controller.learningoutcomes.LearningOutcomesController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class LearningOutcomesMigrationContractTest {
    @Test
    void migrationCreatesTenantRlsAndIdempotencyBoundaries() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/postgres/V94__learning_outcomes_contract.sql"));
        String followUpSql = Files.readString(Path.of("src/main/resources/db/migration/postgres/V95__learning_outcomes_completion_verification.sql"));
        assertThat(sql).contains(
                "CREATE TABLE IF NOT EXISTS learning_paths",
                "CREATE TABLE IF NOT EXISTS learning_path_nodes",
                "CREATE TABLE IF NOT EXISTS competencies",
                "CREATE TABLE IF NOT EXISTS competency_attainments",
                "CREATE TABLE IF NOT EXISTS outcome_certificates",
                "CREATE TABLE IF NOT EXISTS learning_outcome_command_receipts",
                "FORCE ROW LEVEL SECURITY",
                "app.tenant_id",
                "ux_learning_outcome_receipts_key",
                "learning.outcomes.read",
                "learning.outcomes.manage",
                "learning.outcomes.issue_certificate")
                .doesNotContain("DROP TABLE");
        assertThat(followUpSql).contains(
                "learning_path_completions",
                "verification_id",
                "revoked_by",
                "revocation_reason",
                "progress_percent",
                "FORCE ROW LEVEL SECURITY")
                .doesNotContain("DROP TABLE");
    }

    @Test
    void everyControllerBusinessMethodUsesTypedModulePolicy() {
        for (Method method : LearningOutcomesController.class.getDeclaredMethods()) {
            if (method.getName().equals("ok") || method.getName().equals("correlation")) continue;
            assertThat(method.isAnnotationPresent(LearningOutcomesRead.class)
                    || method.isAnnotationPresent(LearningOutcomesWrite.class)
                    || method.isAnnotationPresent(LearningOutcomesIssueCertificate.class))
                    .as("policy for %s", method)
                    .isTrue();
            PreAuthorize preAuthorize = (method.getAnnotation(LearningOutcomesRead.class) != null
                    ? LearningOutcomesRead.class.getAnnotation(PreAuthorize.class)
                    : method.getAnnotation(LearningOutcomesWrite.class) != null
                    ? LearningOutcomesWrite.class.getAnnotation(PreAuthorize.class)
                    : LearningOutcomesIssueCertificate.class.getAnnotation(PreAuthorize.class));
            assertThat(preAuthorize.value()).contains("@methodAuthorization");
        }
    }
}
