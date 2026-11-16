package com.erg.ergspringboot.service.authorization;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.authorization.internal.assignment.AuthorizationDirectRoleUseCase;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.authorization.RoleRepository;
import com.erg.ergspringboot.authorization.internal.audit.AuthorizationMutationAuditService;
import com.erg.ergspringboot.users.RoleAssignmentAudit.UserRef;
import com.erg.ergspringboot.users.UserAuditContext;
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
    @Mock private UserRepository userRepository;
    @Mock private AuthorizationMutationAuditService mutationAuditService;
    private AuthorizationDirectRoleUseCase directRoles;
    private ErgPrincipal actor;
    private UserAuditContext context;

    @BeforeEach
    void setUp() {
        directRoles = new AuthorizationDirectRoleUseCase(userRepository, roleRepository, mutationAuditService,
                org.mockito.Mockito.mock(com.erg.ergspringboot.platform.security.AccountActionGuard.class));
        actor = new ErgPrincipal("admin-1", "session-1", "tenant-a", List.of("system.super_admin"), List.of("*"),
                List.of(), List.of("*"), "erg", "admin", Map.of());
        context = new UserAuditContext("admin-1", "request-1", "127.0.0.1", "JUnit");
    }

    @Test
    void directRoleRemovalBumpsTheTargetUsersAuthorizationVersion() {
        Role role = role("role-teacher", "teacher");
        User target = new User(); target.setId("user-1"); target.setTenantId("tenant-a");
        target.setRoles(new LinkedHashSet<>(List.of(role)));
        when(userRepository.findByIdAndTenantId("user-1", "tenant-a")).thenReturn(Optional.of(target));
        when(roleRepository.findById("role-teacher")).thenReturn(Optional.of(role));

        directRoles.remove("user-1", "role-teacher", "tenant-a", actor, context);

        verify(mutationAuditService).authorizationChanged(
                List.of(new UserRef("user-1", "tenant-a")),
                "role-teacher", "DIRECT_ROLE_REMOVED", context);
    }

    private Role role(String id, String name) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setPermissions(new LinkedHashSet<>());
        return role;
    }
}
