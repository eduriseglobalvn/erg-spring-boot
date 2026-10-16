package com.erg.ergspringboot.enterpriseconnectors;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.config.persistence.JpaRegistrationCatalog;
import com.erg.ergspringboot.controller.enterpriseconnectors.EnterpriseConnectorController;
import com.erg.ergspringboot.enterpriseconnectors.authorization.EnterpriseConnectorsRead;
import com.erg.ergspringboot.enterpriseconnectors.authorization.EnterpriseConnectorsSync;
import com.erg.ergspringboot.enterpriseconnectors.authorization.EnterpriseConnectorsWrite;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class EnterpriseConnectorMigrationContractTest {
    @Test
    void migrationCreatesTenantRlsBoundedRunsAndPermissionBindings() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/postgres/V97__enterprise_connectors_contract.sql"));
        assertThat(sql).contains(
                "CREATE TABLE IF NOT EXISTS enterprise_connectors",
                "CREATE TABLE IF NOT EXISTS enterprise_connector_runs",
                "CREATE TABLE IF NOT EXISTS enterprise_connector_command_receipts",
                "FORCE ROW LEVEL SECURITY",
                "app.tenant_id",
                "retry_count <= 3",
                "enterprise.connectors.read",
                "enterprise.connectors.manage",
                "enterprise.connectors.sync")
                .doesNotContain("DROP TABLE");
    }

    @Test
    void persistenceCatalogOwnsEveryEnterpriseConnectorEntityAndRepository() {
        var module = JpaRegistrationCatalog.modules().stream()
                .filter(value -> value.name().equals("enterpriseconnectors"))
                .findFirst().orElseThrow();
        assertThat(module.enabled()).isTrue();
        assertThat(module.entityCount()).isEqualTo(3);
        assertThat(module.repositoryCount()).isEqualTo(3);
    }

    @Test
    void everyControllerBusinessMethodUsesTypedModulePolicy() {
        for (Method method : EnterpriseConnectorController.class.getDeclaredMethods()) {
            if (method.getName().equals("ok") || method.getName().equals("correlation")) continue;
            boolean typed = method.isAnnotationPresent(EnterpriseConnectorsRead.class)
                    || method.isAnnotationPresent(EnterpriseConnectorsWrite.class)
                    || method.isAnnotationPresent(EnterpriseConnectorsSync.class);
            assertThat(typed).as("policy for %s", method).isTrue();
            PreAuthorize preAuthorize = method.getAnnotation(EnterpriseConnectorsRead.class) != null
                    ? EnterpriseConnectorsRead.class.getAnnotation(PreAuthorize.class)
                    : method.getAnnotation(EnterpriseConnectorsWrite.class) != null
                    ? EnterpriseConnectorsWrite.class.getAnnotation(PreAuthorize.class)
                    : EnterpriseConnectorsSync.class.getAnnotation(PreAuthorize.class);
            assertThat(preAuthorize.value()).contains("@methodAuthorization");
        }
    }
}
