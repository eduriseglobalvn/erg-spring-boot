package com.erg.ergspringboot.service.authorization;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.authorization.internal.access.AuthorizationAccessQueryUseCase;
import com.erg.ergspringboot.authorization.internal.assignment.AuthorizationDirectRoleUseCase;
import com.erg.ergspringboot.authorization.internal.override.AuthorizationPermissionOverrideUseCase;
import com.erg.ergspringboot.authorization.internal.roledefinition.AuthorizationRoleDefinitionUseCase;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.BulkAssignRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.UpdateRoleRequest;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.PermissionRepository;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
import com.erg.ergspringboot.users.UserAuditContext;
import com.erg.ergspringboot.users.UserAuthorizationPort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceSecurityTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserPermissionOverrideRepository overrideRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserAuthorizationPort userAuthorizationPort;

    /** Unauthenticated caller: no principal, no request metadata. */
    private static final UserAuditContext NO_ACTOR = new UserAuditContext(null, null, null, null);

    private AuthorizationRoleDefinitionUseCase roles;
    private AuthorizationDirectRoleUseCase directRoles;
    private AuthorizationPermissionOverrideUseCase overrides;
    private AuthorizationAccessQueryUseCase access;

    @BeforeEach
    void setUp() {
        roles = new AuthorizationRoleDefinitionUseCase(roleRepository, permissionRepository);
        directRoles = new AuthorizationDirectRoleUseCase(userRepository, roleRepository, null);
        overrides = new AuthorizationPermissionOverrideUseCase(userRepository, permissionRepository, overrideRepository, null);
        access = new AuthorizationAccessQueryUseCase(userAuthorizationPort);
    }

    @Test
    void bulkRoleAssignmentRejectsForeignOrMissingUsersBeforeMutation() {
        Role role = role("teacher");
        when(roleRepository.findById("role-teacher")).thenReturn(Optional.of(role));
        when(userRepository.findByTenantIdAndIdInAnyStatus("tenant-a", List.of("foreign-user")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> directRoles.bulk(
                        new BulkAssignRoleRequest(List.of("foreign-user"), "role-teacher"),
                        "tenant-a",
                        null,
                        NO_ACTOR
                ))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRepository, never()).saveAll(any());
    }

    @Test
    void roleRemovalRejectsForeignUserBeforeLoadingRole() {
        when(userRepository.findByIdAndTenantId("foreign-user", "tenant-a"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> directRoles.remove(
                        "foreign-user", "role-teacher", "tenant-a", null, NO_ACTOR
                ))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(roleRepository, never()).findById("role-teacher");
    }

    @Test
    void effectivePermissionsRejectsForeignUserMetadataLookup() {
        when(userAuthorizationPort.find("foreign-user", "tenant-a"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> access.effective("foreign-user", "tenant-a"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userAuthorizationPort, never()).findOverrides(any(), any());
    }

    @Test
    void overrideDeletionRejectsForeignUserBeforeDeletingRecord() {
        when(userRepository.findByIdAndTenantId("foreign-user", "tenant-a"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> overrides.delete(
                        "foreign-user", "override-1", "tenant-a", NO_ACTOR
                ))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(overrideRepository, never()).delete(any());
    }

    @Test
    void protectedRoleDefinitionCannotBeUpdatedWithoutPlatformWildcard() {
        assertThatThrownBy(() -> roles.update(
                        "role-system.rbac_admin",
                        new UpdateRoleRequest(null, "escalated definition", null),
                        null,
                        NO_ACTOR
                ))
                .isInstanceOf(ForbiddenException.class);

        verify(roleRepository, never()).save(any());
    }

    @Test
    void protectedRoleDefinitionCannotBeDeletedWithoutPlatformWildcard() {
        assertThatThrownBy(() -> roles.delete("role-system.rbac_admin", null, NO_ACTOR))
                .isInstanceOf(ForbiddenException.class);

        verify(roleRepository, never()).delete(any());
    }

    @Test
    void ordinaryRoleCatalogNeverExposesGodMode() {
        when(roleRepository.findAllWithPermissionsAndUsers())
                .thenReturn(List.of(role("lms_teacher"), role("system.god_mode")));

        assertThat(roles.list()).extracting(response -> response.name())
                .containsExactly("lms_teacher");
    }

    private Role role(String name) {
        Role role = new Role();
        role.setId("role-" + name);
        role.setName(name);
        role.setPermissions(new java.util.LinkedHashSet<>());
        return role;
    }
}
