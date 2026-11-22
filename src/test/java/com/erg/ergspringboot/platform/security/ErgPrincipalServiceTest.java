package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.users.persistence.auth.AuthSession;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.authorization.Permission;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.auth.AuthSessionRepository;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.auth.AuthTokenService;
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

    @Mock
    private AuthSessionTenantResolver tenantResolver;

    @Test
    void unknownTokenIsResolvedAgainstTheDatabaseOnlyOncePerWindow() {
        ErgPrincipalService service = new ErgPrincipalService(sessionRepository, new AuthTokenService(), tenantResolver);
        when(tenantResolver.resolve(any())).thenReturn(Optional.empty());

        assertThat(service.resolveTenantId("nope")).isEmpty();
        assertThat(service.resolveTenantId("nope")).isEmpty();

        verify(tenantResolver, times(1)).resolve(any());
    }

    @Test
    void aTransientResolverErrorIsNotRememberedAsUnknown() {
        ErgPrincipalService service = new ErgPrincipalService(sessionRepository, new AuthTokenService(), tenantResolver);
        when(tenantResolver.resolve(any())).thenThrow(new IllegalStateException("db down")).thenReturn(Optional.of("erg"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.resolveTenantId("good"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(service.resolveTenantId("good")).contains("erg");
    }

    @Test
    void resolvedTenantIsReusedWithoutASecondResolveTransaction() {
        ErgPrincipalService service = new ErgPrincipalService(sessionRepository, new AuthTokenService(), tenantResolver);
        when(tenantResolver.resolve(any())).thenReturn(Optional.of("erg"));

        assertThat(service.resolveTenantId("good")).contains("erg");
        assertThat(service.resolveTenantId("good")).contains("erg");

        verify(tenantResolver, times(1)).resolve(any());
    }

    @Test
    void bearerLookupResolvesMissingTenantFromTheServerSideSession() {
        ErgPrincipalService service = new ErgPrincipalService(sessionRepository, new AuthTokenService());

        assertThat(service.fromBearerToken("token", " ")).isEmpty();
    }

    @Test
    void bearerLookupRejectsAmbiguousTokenAcrossTenants() {
        AuthTokenService tokenService = new AuthTokenService();
        ErgPrincipalService service = new ErgPrincipalService(sessionRepository, tokenService);
        AuthSession first = activeSession("erg");
        AuthSession second = activeSession("other");
        when(sessionRepository.findByAccessTokenHash(tokenService.hashToken("ambiguous-token")))
                .thenReturn(List.of(first, second));

        assertThat(service.resolveTenantId("ambiguous-token")).isEmpty();
        assertThat(service.fromBearerToken("ambiguous-token")).isEmpty();
    }

    @Test
    void legacyTenantHeaderIsOnlyAConsistencyCheckAgainstResolvedSession() {
        AuthTokenService tokenService = new AuthTokenService();
        ErgPrincipalService service = new ErgPrincipalService(sessionRepository, tokenService);
        AuthSession session = activeSession("erg");
        when(sessionRepository.findByAccessTokenHash(tokenService.hashToken("tenant-bound-token")))
                .thenReturn(List.of(session));

        assertThat(service.fromBearerToken("tenant-bound-token", "other")).isEmpty();
    }

    private AuthSession activeSession(String tenantId) {
        User user = new User();
        user.setId("user-" + tenantId);
        user.setTenantId(tenantId);
        user.setStatus("ACTIVE");
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setTenantId(tenantId);
        session.setSessionId("session-" + tenantId);
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));
        return session;
    }

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
        when(sessionRepository.findByAccessTokenHash(tokenService.hashToken(bearerToken)))
                .thenReturn(List.of(session));
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

        when(sessionRepository.findByAccessTokenHash(tokenService.hashToken(bearerToken)))
                .thenReturn(List.of(session));

        ErgPrincipal principal = service.fromBearerToken(bearerToken, "erg").orElseThrow();

        assertThat(principal.portals()).containsExactly("*");
        assertThat(principal.accessLevel()).isEqualTo("admin");
    }

    @Test
    void trainingOfficeManagesEverySchoolOfItsTenantWithoutBecomingPlatformWide() {
        AuthTokenService tokenService = new AuthTokenService();
        ErgPrincipalService service = new ErgPrincipalService(sessionRepository, tokenService);
        Role role = new Role();
        role.setName("pdt_operator");
        role.setPermissions(new LinkedHashSet<>());
        User user = new User();
        user.setId("u-pdt");
        user.setStatus("ACTIVE");
        user.setRoles(new LinkedHashSet<>(List.of(role)));
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setSessionId("s-pdt");
        session.setTenantId("erg");
        session.setPortal("admin");
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));
        String bearerToken = "pdt-access-token";
        when(sessionRepository.findByAccessTokenHash(tokenService.hashToken(bearerToken)))
                .thenReturn(List.of(session));

        ErgPrincipal principal = service.fromBearerToken(bearerToken, "erg").orElseThrow();

        assertThat(principal.attributes().get("managedEducationUnitIds")).isEqualTo(List.of("*"));
        assertThat(principal.portals()).doesNotContain("*");
        assertThat(principal.accessLevel()).isEqualTo("user");
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
        when(sessionRepository.findByAccessTokenHash(tokenService.hashToken(bearerToken)))
                .thenReturn(List.of(session));

        ErgPrincipal principal = service.fromBearerToken(bearerToken, "erg").orElseThrow();

        assertThat(principal.portals()).isEmpty();
        assertThat(principal.attributes().get("managedEducationUnitIds")).isEqualTo(List.of());
        assertThat(principal.accessLevel()).isEqualTo("user");
    }

    @Test
    void genericAdminPrincipalUsesCapabilityProjectionInsteadOfLoginPortal() {
        AuthTokenService tokenService = new AuthTokenService();
        ErgPrincipalService service = new ErgPrincipalService(sessionRepository, tokenService);
        Role role = new Role();
        role.setName("admin");
        Permission adminAccess = new Permission();
        adminAccess.setName("admin.workspace.access");
        role.setPermissions(new LinkedHashSet<>(List.of(adminAccess)));
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

        when(sessionRepository.findByAccessTokenHash(tokenService.hashToken(bearerToken)))
                .thenReturn(List.of(session));

        ErgPrincipal principal = service.fromBearerToken(bearerToken, "erg").orElseThrow();

        assertThat(principal.portals()).containsExactly("lcms");
    }

    @Test
    void inactiveAccountRevocationReportsAccountDisabledInsteadOfGenericUnauthorized() {
        AuthTokenService tokenService = new AuthTokenService();
        ErgPrincipalService service = new ErgPrincipalService(sessionRepository, tokenService);
        AuthSession session = new AuthSession();
        session.setTenantId("erg");
        session.setRevokedAt(OffsetDateTime.now());
        session.setRevokedReason("status_changed_inactive");
        String bearerToken = "inactive-account-token";

        when(sessionRepository.findByAccessTokenHash(tokenService.hashToken(bearerToken)))
                .thenReturn(List.of(session));

        assertThat(service.authenticationFailureCode(bearerToken, "erg"))
                .contains("ACCOUNT_DISABLED");
    }
}
