package com.erg.ergspringboot.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UserQueryUseCaseArchitectureTest {

    @Test
    void userQueriesLiveInTheUsersModuleInsteadOfTheLegacyMonolith() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.users.internal.query.UserQueryUseCase"))
                .doesNotThrowAnyException();

        String legacy = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/users/UserService.java"));
        assertThat(legacy)
                .doesNotContain("findAdminListItems(")
                .doesNotContain("findByTargetUserIdAndTenantIdOrderByCreatedAtDescIdDesc(");
    }
}
