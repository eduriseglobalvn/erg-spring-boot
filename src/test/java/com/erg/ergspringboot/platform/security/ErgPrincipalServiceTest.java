package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.UserRoleAssignment;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.service.auth.AuthTokenService;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ErgPrincipalServiceTest {

    @Mock
    private AuthSessionRepository sessionRepository;

    @Mock
    private UserRoleAssignmentRepository assignmentRepository;

    @Test
    void activeSystemScopedRoleContributesRuntimePermission() {
        AuthTokenService tokenService = new AuthTokenService();
        ErgPrincipalService service = new ErgPrincipalService(sessionRepository, tokenService);
        service.setUserRoleAssignmentRepository(assignmentRepository);
        Permission permission = new Permission();
        permission.setName("rbac.role.read");
        Role role = new Role();
        role.setName("it_operator");
        role.setPermissions(new LinkedHashSet<>(List.of(permission)));
        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setRole(role);
        assignment.setScopeType("SYSTEM");
        assignment.setTenantId("erg");
        assignment.setUserId("u-it");
        User user = new User();
        user.setId("u-it");
        user.setTenantId("erg");
        user.setStatus("ACTIVE");
        user.setRoles(new LinkedHashSet<>());
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setSessionId("s-it");
        session.setTenantId("erg");
        session.setPortal("lcms");
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));
        String bearerToken = "it-access-token";
        when(sessionRepository.findPrincipalByAccessTokenHashAndTenantId(tokenService.hashToken(bearerToken), "erg"))
                .thenReturn(Optional.of(session));
        when(assignmentRepository.findActiveForUser(eq("u-it"), eq("erg"), any(OffsetDateTime.class)))
                .thenReturn(List.of(assignment));

        ErgPrincipal principal = service.fromBearerToken(bearerToken, "erg").orElseThrow();

        assertThat(principal.permissions()).contains("rbac.role.read");
        assertThat(principal.roles()).contains("it_operator");
    }

    @Test
    void systemSuperAdminPrincipalHasPlatformWidePortalEntitlement() {
        AuthTokenService tokenService = new AuthTokenService();
        ErgPrincipalService service = new ErgPrincipalService(sessionRepository, tokenService);
        Role role = new Role();
        role.setName("system.super_admin");
        Permission wildcard = new Permission();
        wildcard.setName("*");
        role.setPermissions(new LinkedHashSet<>(List.of(wildcard)));
        User user = new User();
        user.setId("u-admin");
        user.setStatus("ACTIVE");
        user.setRoles(new LinkedHashSet<>(List.of(role)));
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setSessionId("s-admin");
        session.setTenantId("erg");
        session.setPortal("admin");
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));
        String bearerToken = "admin-access-token";

        when(sessionRepository.findPrincipalByAccessTokenHashAndTenantId(tokenService.hashToken(bearerToken), "erg"))
                .thenReturn(Optional.of(session));

        ErgPrincipal principal = service.fromBearerToken(bearerToken, "erg").orElseThrow();

        assertThat(principal.portals()).containsExactly("*");
        assertThat(principal.accessLevel()).isEqualTo("admin");
    }

    @Test
    void systemRoleNameWithoutDatabasePermissionDoesNotGrantPlatformScope() {
        AuthTokenService tokenService = new AuthTokenService();
        ErgPrincipalService service = new ErgPrincipalService(sessionRepository, tokenService);
        Role role = new Role();
        role.setName("system.super_admin");
        role.setPermissions(new LinkedHashSet<>());
        User user = new User();
        user.setId("u-forged-admin");
        user.setStatus("ACTIVE");
        user.setRoles(new LinkedHashSet<>(List.of(role)));
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setSessionId("s-forged-admin");
        session.setTenantId("erg");
        session.setPortal("admin");
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));
        String bearerToken = "forged-admin-access-token";
        when(sessionRepository.findPrincipalByAccessTokenHashAndTenantId(tokenService.hashToken(bearerToken), "erg"))
                .thenReturn(Optional.of(session));

        ErgPrincipal principal = service.fromBearerToken(bearerToken, "erg").orElseThrow();

        assertThat(principal.portals()).containsExactly("admin");
        assertThat(principal.attributes().get("managedEducationUnitIds")).isEqualTo(List.of());
        assertThat(principal.accessLevel()).isEqualTo("user");
    }

    @Test
    void genericAdminPrincipalRemainsLimitedToItsLoginPortal() {
        AuthTokenService tokenService = new AuthTokenService();
        ErgPrincipalService service = new ErgPrincipalService(sessionRepository, tokenService);
        Role role = new Role();
        role.setName("admin");
        User user = new User();
        user.setId("u-local-admin");
        user.setStatus("ACTIVE");
        user.setRoles(new LinkedHashSet<>(List.of(role)));
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setSessionId("s-local-admin");
        session.setTenantId("erg");
        session.setPortal("admin");
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));
        String bearerToken = "local-admin-access-token";

        when(sessionRepository.findPrincipalByAccessTokenHashAndTenantId(tokenService.hashToken(bearerToken), "erg"))
                .thenReturn(Optional.of(session));

        ErgPrincipal principal = service.fromBearerToken(bearerToken, "erg").orElseThrow();

        assertThat(principal.portals()).containsExactly("admin");
    }

    @Test
    void inactiveAccountRevocationReportsAccountDisabledInsteadOfGenericUnauthorized() {
        AuthTokenService tokenService = new AuthTokenService();
        ErgPrincipalService service = new ErgPrincipalService(sessionRepository, tokenService);
        AuthSession session = new AuthSession();
        session.setRevokedAt(OffsetDateTime.now());
        session.setRevokedReason("status_changed_inactive");
        String bearerToken = "inactive-account-token";

        when(sessionRepository.findByAccessTokenHashAndTenantId(tokenService.hashToken(bearerToken), "erg"))
                .thenReturn(Optional.of(session));

        assertThat(service.authenticationFailureCode(bearerToken, "erg"))
                .contains("ACCOUNT_DISABLED");
    }
}
