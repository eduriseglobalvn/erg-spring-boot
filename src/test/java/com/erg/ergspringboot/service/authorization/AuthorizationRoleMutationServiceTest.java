package com.erg.ergspringboot.service.authorization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.CreateRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.AddPermissionOverrideRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.UpdateRoleRequest;
import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserRoleAssignment;
import com.erg.ergspringboot.model.authorization.UserPermissionOverride;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.PermissionRepository;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
import com.erg.ergspringboot.repository.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.service.authorization.AuthorizationMutationAuditService.UserRef;
import com.erg.ergspringboot.service.users.UserService.AuditContext;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationRoleMutationServiceTest {
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserPermissionOverrideRepository overrideRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserRoleAssignmentRepository assignmentRepository;
    @Mock private AuthorizationMutationAuditService mutationAuditService;
    private AuthorizationService service;
    private ErgPrincipal actor;
    private AuditContext context;

    @BeforeEach
    void setUp() {
        service = new AuthorizationService(roleRepository, permissionRepository, overrideRepository, userRepository);
        service.setUserRoleAssignmentRepository(assignmentRepository);
        service.setMutationAuditService(mutationAuditService);
        actor = new ErgPrincipal("admin-1", "session-1", "tenant-a", List.of("admin"), List.of("*"),
                List.of(), List.of("*"), "erg", "admin", Map.of());
        context = new AuditContext("admin-1", "request-1", "127.0.0.1", "JUnit");
    }

    @Test
    void roleCreationWritesRequestAwareAuditWithoutInventingAffectedUsers() {
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createRole(new CreateRoleRequest("teacher", "Teacher", List.of()), actor, context);

        assertThat(response.updatedBy()).isEqualTo("admin-1");
        verify(mutationAuditService).recordRoleMutation(
                eq("tenant-a"), any(String.class), eq("ROLE_CREATED"), eq(context),
                eq(Map.of("roleName", "teacher")));
        org.mockito.Mockito.verify(mutationAuditService, org.mockito.Mockito.never()).authorizationChanged(
                any(), any(), any(), any());
    }

    @Test
    void roleUpdateBumpsDirectAndActiveScopedUsersAndAuditsTheMutation() {
        Permission permission = new Permission(); permission.setId("perm-1"); permission.setName("lms.assignment.create");
        User direct = new User(); direct.setId("user-direct"); direct.setTenantId("tenant-a");
        Role role = role("role-teacher", "teacher"); role.setUsers(new LinkedHashSet<>(List.of(direct)));
        UserRoleAssignment scoped = new UserRoleAssignment();
        scoped.setUserId("user-scoped"); scoped.setTenantId("tenant-b"); scoped.setRoleId("role-teacher");
        when(roleRepository.findById("role-teacher")).thenReturn(Optional.of(role));
        when(permissionRepository.findByNameInIgnoreCase(List.of("lms.assignment.create")))
                .thenReturn(List.of(permission));
        when(assignmentRepository.findActiveForRole(eq("role-teacher"), any(OffsetDateTime.class)))
                .thenReturn(List.of(scoped));
        when(roleRepository.save(role)).thenReturn(role);

        var response = service.updateRole(
                "role-teacher",
                new UpdateRoleRequest(null, "Updated", List.of("lms.assignment.create")),
                actor,
                context
        );

        assertThat(response.updatedBy()).isEqualTo("admin-1");
        verify(mutationAuditService).authorizationChanged(
                List.of(new UserRef("user-direct", "tenant-a"), new UserRef("user-scoped", "tenant-b")),
                "role-teacher", "ROLE_UPDATED", context);
        verify(mutationAuditService).recordRoleMutation(
                "tenant-a", "role-teacher", "ROLE_UPDATED", context, Map.of("roleName", "teacher"));
    }

    @Test
    void roleDeletionCapturesAffectedUsersBeforeDeleteAndPublishesVersionChanges() {
        User direct = new User(); direct.setId("user-direct"); direct.setTenantId("tenant-a");
        Role role = role("role-teacher", "teacher"); role.setUsers(new LinkedHashSet<>(List.of(direct)));
        when(roleRepository.findById("role-teacher")).thenReturn(Optional.of(role));
        when(assignmentRepository.findActiveForRole(eq("role-teacher"), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        service.deleteRole("role-teacher", actor, context);

        verify(roleRepository).delete(role);
        verify(mutationAuditService).authorizationChanged(
                List.of(new UserRef("user-direct", "tenant-a")), "role-teacher", "ROLE_DELETED", context);
        verify(mutationAuditService).recordRoleMutation(
                "tenant-a", "role-teacher", "ROLE_DELETED", context, Map.of("roleName", "teacher"));
    }

    @Test
    void directRoleRemovalBumpsTheTargetUsersAuthorizationVersion() {
        Role role = role("role-teacher", "teacher");
        User target = new User(); target.setId("user-1"); target.setTenantId("tenant-a");
        target.setRoles(new LinkedHashSet<>(List.of(role)));
        when(userRepository.findByIdAndTenantId("user-1", "tenant-a")).thenReturn(Optional.of(target));
        when(roleRepository.findById("role-teacher")).thenReturn(Optional.of(role));

        service.removeRole("user-1", "role-teacher", "tenant-a", actor, context);

        verify(mutationAuditService).authorizationChanged(
                List.of(new UserRef("user-1", "tenant-a")),
                "role-teacher", "DIRECT_ROLE_REMOVED", context);
    }

    @Test
    void directPermissionOverrideBumpsTheTargetUsersAuthorizationVersion() {
        Permission permission = new Permission(); permission.setId("perm-1"); permission.setName("lms.assignment.create");
        User target = new User(); target.setId("user-1"); target.setTenantId("tenant-a");
        when(userRepository.findByIdAndTenantId("user-1", "tenant-a")).thenReturn(Optional.of(target));
        when(permissionRepository.findByNameInIgnoreCase(List.of("lms.assignment.create")))
                .thenReturn(List.of(permission));
        when(overrideRepository.save(any(UserPermissionOverride.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.addPermissionOverride(
                "user-1",
                new AddPermissionOverrideRequest("GRANT", "lms.assignment.create", "temporary", null),
                "admin-1", "tenant-a", context);

        verify(mutationAuditService).authorizationChanged(
                List.of(new UserRef("user-1", "tenant-a")),
                "perm-1", "PERMISSION_OVERRIDE_ADDED", context);
    }

    private Role role(String id, String name) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setPermissions(new LinkedHashSet<>());
        return role;
    }
}
