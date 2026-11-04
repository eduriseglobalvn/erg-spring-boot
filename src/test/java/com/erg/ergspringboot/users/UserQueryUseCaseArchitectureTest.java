package com.erg.ergspringboot.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class UserQueryUseCaseArchitectureTest {

    @Test
    void userQueriesLiveInTheUsersModuleInsteadOfTheLegacyMonolith() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.users.internal.query.UserQueryUseCase"))
                .doesNotThrowAnyException();

        String legacy = legacyUserServiceSource();
        assertThat(legacy)
                .doesNotContain("findAdminListItems(")
                .doesNotContain("findByTargetUserIdAndTenantIdOrderByCreatedAtDescIdDesc(");
    }

    @Test
    void profileMutationsLiveInTheirOwnUseCase() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.users.internal.profile.UserProfileUseCase"))
                .doesNotThrowAnyException();

        String legacy = legacyUserServiceSource();
        assertThat(legacy).doesNotContain("applyProfileUpdate(");
    }

    @Test
    void passwordMutationsLiveInTheirOwnUseCase() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.users.internal.password.UserPasswordUseCase"))
                .doesNotThrowAnyException();
        String legacy = legacyUserServiceSource();
        assertThat(legacy).doesNotContain("passwordService.matches(");
    }

    @Test
    void sessionMutationsLiveInTheirOwnUseCase() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.users.internal.session.UserSessionUseCase"))
                .doesNotThrowAnyException();
        String legacy = legacyUserServiceSource();
        assertThat(legacy)
                .doesNotContain("findByUserIdAndTenantIdOrderByLastActiveAtDesc(")
                .doesNotContain("findBySessionIdAndUserIdAndTenantId(");
    }

    @Test
    void accountProvisioningLivesInItsOwnUseCase() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.users.internal.create.UserProvisioningUseCase"))
                .doesNotThrowAnyException();
        String legacy = legacyUserServiceSource();
        assertThat(legacy).doesNotContain("request.primaryEmail().trim().toLowerCase()");
    }

    @Test
    void accountLifecycleLivesInItsOwnUseCase() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.users.internal.lifecycle.UserLifecycleUseCase"))
                .doesNotThrowAnyException();
        String legacy = legacyUserServiceSource();
        assertThat(legacy).doesNotContain("normalizeStatus(request.status())");
    }

    @Test
    void roleAndPermissionMutationsLiveInTheirOwnUseCase() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.users.internal.roleassignment.UserRoleAssignmentUseCase"))
                .doesNotThrowAnyException();
        String legacy = legacyUserServiceSource();
        assertThat(legacy).doesNotContain("permissionOverrideRepository.deleteByUserId(");
    }

    // The deprecated UserService adapter was retired (P3-4); an absent file reads as empty so the guards still apply.
    private static String legacyUserServiceSource() throws java.io.IOException {
        Path legacy = Path.of("src/main/java/com/erg/ergspringboot/service/users/UserService.java");
        return Files.exists(legacy) ? Files.readString(legacy) : "";
    }

    @Test
    void publicUsersFacadeDoesNotStoreTheLegacyMonolith() {
        for (Field field : UserOperations.class.getDeclaredFields()) {
            assertThat(field.getType().getName()).isNotEqualTo(
                    "com.erg.ergspringboot.service.users.UserService");
        }
    }
}
