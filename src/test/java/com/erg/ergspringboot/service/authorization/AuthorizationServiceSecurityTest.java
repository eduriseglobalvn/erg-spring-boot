package com.erg.ergspringboot.service.authorization;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.authorization.internal.access.AuthorizationAccessQueryUseCase;
import com.erg.ergspringboot.authorization.internal.assignment.AuthorizationDirectRoleUseCase;
import com.erg.ergspringboot.authorization.internal.roledefinition.AuthorizationRoleDefinitionUseCase;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.BulkAssignRoleRequest;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.authorization.PermissionRepository;
import com.erg.ergspringboot.users.persistence.authorization.RoleRepository;
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
    @Mock private UserRepository userRepository;
    @Mock private UserAuthorizationPort userAuthorizationPort;

    /** Unauthenticated caller: no principal, no request metadata. */
    private static final UserAuditContext NO_ACTOR = new UserAuditContext(null, null, null, null);

    private AuthorizationRoleDefinitionUseCase roles;
    private AuthorizationDirectRoleUseCase directRoles;
    private AuthorizationAccessQueryUseCase access;

    @BeforeEach
    void setUp() {
        roles = new AuthorizationRoleDefinitionUseCase(roleRepository);
        directRoles = new AuthorizationDirectRoleUseCase(userRepository, roleRepository, null,
                org.mockito.Mockito.mock(com.erg.ergspringboot.platform.security.AccountActionGuard.class));
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
                        actor(),
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

    }

    @Test
    void ordinaryRoleCatalogNeverExposesGodMode() {
        when(roleRepository.findAllWithPermissionsAndUsers())
                .thenReturn(List.of(role("lms_teacher"), role("system.god_mode")));

        assertThat(roles.list()).extracting(response -> response.name())
                .containsExactly("lms_teacher");
    }

    /** An authenticated actor holding no permissions: may grant only roles that carry none (no escalation). */
    private static com.erg.ergspringboot.platform.security.ErgPrincipal actor() {
        return com.erg.ergspringboot.platform.security.ErgPrincipal.authenticated("actor-1", "session-1", "tenant-a",
                List.of("hr"), List.of(), List.of(), List.of(), "employee", "admin", Map.of());
    }

    private Role role(String name) {
        Role role = new Role();
        role.setId("role-" + name);
        role.setName(name);
        role.setPermissions(new java.util.LinkedHashSet<>());
        return role;
    }
}
