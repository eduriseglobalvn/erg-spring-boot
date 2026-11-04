package com.erg.ergspringboot.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthorizationUseCaseArchitectureTest {
    @Test
    void permissionCatalogHasAnInternalUseCase() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.authorization.internal.catalog.AuthorizationPermissionCatalogUseCase"))
                .doesNotThrowAnyException();
        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/authorization/AuthorizationCommandOperations.java"));
        assertThat(facade).doesNotContain("permissionRepository.searchCatalog(");
    }

    @Test
    void roleDefinitionsHaveAnInternalUseCase() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.authorization.internal.roledefinition.AuthorizationRoleDefinitionUseCase"))
                .doesNotThrowAnyException();
        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/authorization/AuthorizationCommandOperations.java"));
        assertThat(facade).doesNotContain("protectedRolePolicy.assertRoleDefinitionAllowed(");
    }

    @Test
    void authorizationAccessQueriesHaveAnInternalUseCase() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.authorization.internal.access.AuthorizationAccessQueryUseCase"))
                .doesNotThrowAnyException();
        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/authorization/AuthorizationCommandOperations.java"));
        assertThat(facade).doesNotContain("userRepository.findWithDetailsByIdAndTenantId(");
    }

    @Test
    void directRoleMutationsHaveAnInternalUseCase() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.authorization.internal.assignment.AuthorizationDirectRoleUseCase"))
                .doesNotThrowAnyException();
        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/authorization/AuthorizationCommandOperations.java"));
        assertThat(facade).doesNotContain("userRepository.findByIdAndTenantId(");
    }

    @Test
    void permissionOverridesHaveAnInternalUseCase() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.authorization.internal.override.AuthorizationPermissionOverrideUseCase"))
                .doesNotThrowAnyException();
        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/authorization/AuthorizationCommandOperations.java"));
        assertThat(facade).doesNotContain("overrideRepository.findById(");
    }
}
