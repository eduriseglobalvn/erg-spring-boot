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

    @Test
    void profileMutationsLiveInTheirOwnUseCase() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.users.internal.profile.UserProfileUseCase"))
                .doesNotThrowAnyException();

        String legacy = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/users/UserService.java"));
        assertThat(legacy).doesNotContain("applyProfileUpdate(");
    }

    @Test
    void passwordMutationsLiveInTheirOwnUseCase() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.users.internal.password.UserPasswordUseCase"))
                .doesNotThrowAnyException();
        String legacy = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/users/UserService.java"));
        assertThat(legacy).doesNotContain("passwordService.matches(");
    }

    @Test
    void sessionMutationsLiveInTheirOwnUseCase() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.users.internal.session.UserSessionUseCase"))
                .doesNotThrowAnyException();
        String legacy = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/users/UserService.java"));
        assertThat(legacy)
                .doesNotContain("findByUserIdAndTenantIdOrderByLastActiveAtDesc(")
                .doesNotContain("findBySessionIdAndUserIdAndTenantId(");
    }

    @Test
    void accountProvisioningLivesInItsOwnUseCase() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.users.internal.create.UserProvisioningUseCase"))
                .doesNotThrowAnyException();
        String legacy = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/users/UserService.java"));
        assertThat(legacy).doesNotContain("request.primaryEmail().trim().toLowerCase()");
    }

    @Test
    void accountLifecycleLivesInItsOwnUseCase() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.users.internal.lifecycle.UserLifecycleUseCase"))
                .doesNotThrowAnyException();
        String legacy = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/users/UserService.java"));
        assertThat(legacy).doesNotContain("normalizeStatus(request.status())");
    }
}
