package com.erg.ergspringboot.service.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.AssignScopedRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.RevokeScopedRoleRequest;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserRoleAssignment;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.authorization.UserRoleAssignmentRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScopedRoleAssignmentServiceTest {
    @Mock private UserRoleAssignmentRepository assignmentRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuthorizationMutationAuditService mutationAuditService;
    private ScopedRoleAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new ScopedRoleAssignmentService(
                assignmentRepository, roleRepository, userRepository, mutationAuditService);
    }

    @Test
    void assignmentPersistsTenantScopeExpiryActorAndAudit() {
        User user = new User(); user.setId("user-1"); user.setTenantId("tenant-a");
        Role role = new Role(); role.setId("role-teacher"); role.setName("lms_teacher");
        ErgPrincipal actor = new ErgPrincipal("admin-1", "session-1", "tenant-a", List.of("admin"), List.of("rbac.role.assign"), List.of(), List.of("lcms"), "erg", "admin", Map.of());
        OffsetDateTime expiresAt = OffsetDateTime.parse("2027-01-01T00:00:00Z");
        when(userRepository.findByIdAndTenantId("user-1", "tenant-a")).thenReturn(Optional.of(user));
        when(roleRepository.findById("role-teacher")).thenReturn(Optional.of(role));
        when(assignmentRepository.save(any(UserRoleAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.assign(
                "user-1", "tenant-a",
                new AssignScopedRoleRequest("role-teacher", "SCHOOL", "school-1", expiresAt), actor
        );

        ArgumentCaptor<UserRoleAssignment> captor = ArgumentCaptor.forClass(UserRoleAssignment.class);
        verify(assignmentRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo("tenant-a");
        assertThat(captor.getValue().getScopeType()).isEqualTo("SCHOOL");
        assertThat(captor.getValue().getScopeId()).isEqualTo("school-1");
        assertThat(captor.getValue().getAssignedBy()).isEqualTo("admin-1");
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(expiresAt);
        assertThat(response.roleName()).isEqualTo("lms_teacher");
        verify(mutationAuditService).authorizationChanged(
                List.of(new AuthorizationMutationAuditService.UserRef("user-1", "tenant-a")),
                "role-teacher",
                "SCOPED_ROLE_ASSIGNED",
                new com.erg.ergspringboot.service.users.UserService.AuditContext("admin-1", null, null, null)
        );
    }

    @Test
    void revokePersistsActorReasonAndBumpsOnlyTheOwnedTenantUsersAuthorizationVersion() {
        User user = new User(); user.setId("user-1"); user.setTenantId("tenant-a");
        Role role = new Role(); role.setId("role-teacher"); role.setName("lms_teacher");
        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setId("assignment-1");
        assignment.setUserId("user-1");
        assignment.setTenantId("tenant-a");
        assignment.setRoleId("role-teacher");
        assignment.setRole(role);
        ErgPrincipal actor = new ErgPrincipal("admin-1", "session-1", "tenant-a", List.of("admin"),
                List.of("rbac.role.assign"), List.of(), List.of("lcms"), "erg", "admin", Map.of());
        when(userRepository.findByIdAndTenantId("user-1", "tenant-a")).thenReturn(Optional.of(user));
        when(assignmentRepository.findWithRoleById("assignment-1")).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(UserRoleAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.revoke(
                "user-1", "assignment-1", "tenant-a",
                new RevokeScopedRoleRequest("left department"), actor,
                new com.erg.ergspringboot.service.users.UserService.AuditContext(
                        "admin-1", "request-1", "127.0.0.1", "JUnit")
        );

        assertThat(assignment.getRevokedAt()).isNotNull();
        assertThat(assignment.getRevokedBy()).isEqualTo("admin-1");
        assertThat(assignment.getRevokeReason()).isEqualTo("left department");
        assertThat(response.revokedAt()).isEqualTo(assignment.getRevokedAt());
        assertThat(response.revokeReason()).isEqualTo("left department");
        verify(mutationAuditService).authorizationChanged(
                List.of(new AuthorizationMutationAuditService.UserRef("user-1", "tenant-a")),
                "role-teacher",
                "SCOPED_ROLE_REVOKED",
                new com.erg.ergspringboot.service.users.UserService.AuditContext(
                        "admin-1", "request-1", "127.0.0.1", "JUnit")
        );
    }

    @Test
    void repeatedRevokeReturnsExistingStateWithoutAnotherVersionChange() {
        User user = new User(); user.setId("user-1"); user.setTenantId("tenant-a");
        Role role = new Role(); role.setId("role-teacher"); role.setName("lms_teacher");
        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setId("assignment-1"); assignment.setUserId("user-1"); assignment.setTenantId("tenant-a");
        assignment.setRoleId("role-teacher"); assignment.setRole(role);
        assignment.setRevokedAt(OffsetDateTime.parse("2026-07-01T00:00:00Z"));
        assignment.setRevokedBy("admin-old"); assignment.setRevokeReason("already revoked");
        ErgPrincipal actor = new ErgPrincipal("admin-1", "session-1", "tenant-a", List.of("admin"),
                List.of("rbac.role.assign"), List.of(), List.of("lcms"), "erg", "admin", Map.of());
        when(userRepository.findByIdAndTenantId("user-1", "tenant-a")).thenReturn(Optional.of(user));
        when(assignmentRepository.findWithRoleById("assignment-1")).thenReturn(Optional.of(assignment));

        var response = service.revoke(
                "user-1", "assignment-1", "tenant-a",
                new RevokeScopedRoleRequest("retry"), actor, null);

        assertThat(response.revokedBy()).isEqualTo("admin-old");
        assertThat(response.revokeReason()).isEqualTo("already revoked");
        org.mockito.Mockito.verify(assignmentRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(mutationAuditService);
    }

    @Test
    void revokeDoesNotExposeOrMutateAnAssignmentOwnedByAnotherTenantUser() {
        User user = new User(); user.setId("user-1"); user.setTenantId("tenant-a");
        UserRoleAssignment foreign = new UserRoleAssignment();
        foreign.setId("assignment-foreign"); foreign.setUserId("user-2"); foreign.setTenantId("tenant-b");
        when(userRepository.findByIdAndTenantId("user-1", "tenant-a")).thenReturn(Optional.of(user));
        when(assignmentRepository.findWithRoleById("assignment-foreign")).thenReturn(Optional.of(foreign));
        ErgPrincipal actor = new ErgPrincipal("admin-1", "session-1", "tenant-a", List.of("admin"),
                List.of("rbac.role.assign"), List.of(), List.of("lcms"), "erg", "admin", Map.of());

        assertThatThrownBy(() -> service.revoke(
                "user-1", "assignment-foreign", "tenant-a", null, actor, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("role assignment not found");

        org.mockito.Mockito.verify(assignmentRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(mutationAuditService);
    }
}
