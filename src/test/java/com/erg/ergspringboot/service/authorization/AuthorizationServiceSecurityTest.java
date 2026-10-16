package com.erg.ergspringboot.service.authorization;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.BulkAssignRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.UpdateRoleRequest;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.PermissionRepository;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
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

    private AuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new AuthorizationService(roleRepository, permissionRepository, overrideRepository, userRepository);
    }

    @Test
    void bulkRoleAssignmentRejectsForeignOrMissingUsersBeforeMutation() {
        Role role = role("teacher");
        when(roleRepository.findById("role-teacher")).thenReturn(Optional.of(role));
        when(userRepository.findByTenantIdAndIdInAnyStatus("tenant-a", List.of("foreign-user")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.bulkAssignRole(
                        new BulkAssignRoleRequest(List.of("foreign-user"), "role-teacher"),
                        "tenant-a",
                        null
                ))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRepository, never()).saveAll(any());
    }

    @Test
    void roleRemovalRejectsForeignUserBeforeLoadingRole() {
        when(userRepository.findByIdAndTenantId("foreign-user", "tenant-a"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeRole(
                        "foreign-user", "role-teacher", "tenant-a", null
                ))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(roleRepository, never()).findById("role-teacher");
    }

    @Test
    void effectivePermissionsRejectsForeignUserMetadataLookup() {
        when(userRepository.findByIdAndTenantId("foreign-user", "tenant-a"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.effectivePermissions("foreign-user", "tenant-a"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(overrideRepository, never()).findByUserId("foreign-user");
    }

    @Test
    void overrideDeletionRejectsForeignUserBeforeDeletingRecord() {
        when(userRepository.findByIdAndTenantId("foreign-user", "tenant-a"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePermissionOverride(
                        "foreign-user", "override-1", "tenant-a"
                ))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(overrideRepository, never()).delete(any());
    }

    @Test
    void protectedRoleDefinitionCannotBeUpdatedWithoutPlatformWildcard() {
        Role protectedRole = role("system.rbac_admin");
        when(roleRepository.findById("role-system.rbac_admin")).thenReturn(Optional.of(protectedRole));

        assertThatThrownBy(() -> service.updateRole(
                        "role-system.rbac_admin",
                        new UpdateRoleRequest(null, "escalated definition", null)
                ))
                .isInstanceOf(ForbiddenException.class);

        verify(roleRepository, never()).save(any());
    }

    @Test
    void protectedRoleDefinitionCannotBeDeletedWithoutPlatformWildcard() {
        Role protectedRole = role("system.rbac_admin");
        when(roleRepository.findById("role-system.rbac_admin")).thenReturn(Optional.of(protectedRole));

        assertThatThrownBy(() -> service.deleteRole("role-system.rbac_admin"))
                .isInstanceOf(ForbiddenException.class);

        verify(roleRepository, never()).delete(any());
    }

    @Test
    void ordinaryRoleCatalogNeverExposesGodMode() {
        when(roleRepository.findAllWithPermissionsAndUsers())
                .thenReturn(List.of(role("lms_teacher"), role("system.god_mode")));

        assertThat(service.listRoles()).extracting(response -> response.name())
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
