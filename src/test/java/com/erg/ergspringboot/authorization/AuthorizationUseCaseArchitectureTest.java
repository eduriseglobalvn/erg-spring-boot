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
        String legacy = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/authorization/AuthorizationService.java"));
        assertThat(legacy).doesNotContain("permissionRepository.searchCatalog(");
    }

    @Test
    void roleDefinitionsHaveAnInternalUseCase() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.authorization.internal.roledefinition.AuthorizationRoleDefinitionUseCase"))
                .doesNotThrowAnyException();
        String legacy = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/authorization/AuthorizationService.java"));
        assertThat(legacy).doesNotContain("protectedRolePolicy.assertRoleDefinitionAllowed(");
    }

    @Test
    void authorizationAccessQueriesHaveAnInternalUseCase() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.authorization.internal.access.AuthorizationAccessQueryUseCase"))
                .doesNotThrowAnyException();
        String legacy = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/authorization/AuthorizationService.java"));
        assertThat(legacy).doesNotContain("userRepository.findWithDetailsByIdAndTenantId(");
    }
}
