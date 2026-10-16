package com.erg.ergspringboot.learningstandards;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.config.persistence.JpaRegistrationCatalog;
import com.erg.ergspringboot.controller.learningstandards.LearningStandardsController;
import com.erg.ergspringboot.learningstandards.authorization.LearningStandardsLaunch;
import com.erg.ergspringboot.learningstandards.authorization.LearningStandardsRead;
import com.erg.ergspringboot.learningstandards.authorization.LearningStandardsWrite;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class LearningStandardsMigrationContractTest {
    @Test
    void migrationCreatesBoundedTenantRlsAndPermissionBoundaries() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/postgres/V96__learning_standards_contract.sql"));
        assertThat(sql).contains(
                "CREATE TABLE IF NOT EXISTS learning_standard_packages",
                "CREATE TABLE IF NOT EXISTS learning_standard_lti_registrations",
                "CREATE TABLE IF NOT EXISTS learning_standard_xapi_statements",
                "CREATE TABLE IF NOT EXISTS learning_standard_command_receipts",
                "FORCE ROW LEVEL SECURITY",
                "app.tenant_id",
                "ux_learning_standard_receipts_key",
                "learning.standards.read",
                "learning.standards.manage",
                "learning.standards.launch")
                .doesNotContain("DROP TABLE");
    }

    @Test
    void persistenceCatalogOwnsEveryLearningStandardsEntityAndRepository() {
        var module = JpaRegistrationCatalog.modules().stream()
                .filter(value -> value.name().equals("learningstandards"))
                .findFirst()
                .orElseThrow();
        assertThat(module.enabled()).isTrue();
        assertThat(module.entityCount()).isEqualTo(4);
        assertThat(module.repositoryCount()).isEqualTo(4);
    }

    @Test
    void everyControllerBusinessMethodUsesTypedModulePolicy() {
        for (Method method : LearningStandardsController.class.getDeclaredMethods()) {
            if (method.getName().equals("ok") || method.getName().equals("correlation")) continue;
            boolean typed = method.isAnnotationPresent(LearningStandardsRead.class)
                    || method.isAnnotationPresent(LearningStandardsWrite.class)
                    || method.isAnnotationPresent(LearningStandardsLaunch.class);
            assertThat(typed).as("policy for %s", method).isTrue();
            PreAuthorize preAuthorize = method.getAnnotation(LearningStandardsRead.class) != null
                    ? LearningStandardsRead.class.getAnnotation(PreAuthorize.class)
                    : method.getAnnotation(LearningStandardsWrite.class) != null
                    ? LearningStandardsWrite.class.getAnnotation(PreAuthorize.class)
                    : LearningStandardsLaunch.class.getAnnotation(PreAuthorize.class);
            assertThat(preAuthorize.value()).contains("@methodAuthorization");
        }
    }
}
