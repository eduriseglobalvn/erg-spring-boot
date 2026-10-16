package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class OidcPrincipalBridgeFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bridgesActiveInternalUserWithDatabaseAuthorizationOnly() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        OidcUser oidcUser = oidcUser(" Staff-01 ", "untrusted@example.test");
        User user = activeUser();
        when(userRepository.findWithDetailsByTenantAndIdentifier("tenant-1", "staff-01", "staff-01"))
                .thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                oidcUser,
                "oidc-credential",
                List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_SUPER_ADMIN"))
        ));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "tenant-1");

        new OidcPrincipalBridgeFilter(userRepository, "tenant-1").doFilter(
                request,
                new MockHttpServletResponse(),
                (servletRequest, servletResponse) -> {
                    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                    assertThat(principal).isInstanceOf(ErgPrincipal.class);
                    ErgPrincipal ergPrincipal = (ErgPrincipal) principal;
                    assertThat(ergPrincipal.userId()).isEqualTo("user-1");
                    assertThat(ergPrincipal.sessionId()).isEqualTo(request.getSession().getId());
                    assertThat(ergPrincipal.roles()).containsExactly("lms.teacher");
                    assertThat(ergPrincipal.permissions()).containsExactly("lms.quiz.read");
                    assertThat(ergPrincipal.portals()).containsExactly("lms");
                    assertThat(ergPrincipal.permissions()).doesNotContain("*");
                }
        );

        verify(userRepository).findWithDetailsByTenantAndIdentifier("tenant-1", "staff-01", "staff-01");
    }

    @Test
    void systemRoleNameWithoutDatabaseWildcardDoesNotCreateOidcPlatformAuthority() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        OidcUser oidcUser = oidcUser("forged-admin", null);
        User user = activeUser();
        Role role = new Role();
        role.setName("system.super_admin");
        role.setPermissions(Set.of());
        user.setRoles(Set.of(role));
        when(userRepository.findWithDetailsByTenantAndIdentifier("tenant-1", "forged-admin", "forged-admin"))
                .thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(oidcUser, null, List.of())
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "tenant-1");
        new OidcPrincipalBridgeFilter(userRepository, "tenant-1").doFilter(
                request,
                new MockHttpServletResponse(),
                (servletRequest, servletResponse) -> {
                    ErgPrincipal principal = (ErgPrincipal) SecurityContextHolder.getContext()
                            .getAuthentication().getPrincipal();
                    assertThat(principal.portals()).isEmpty();
                    assertThat(principal.accessLevel()).isEqualTo("user");
                    assertThat(principal.permissions()).doesNotContain("*");
                }
        );
    }

    @Test
    void oidcBridgeDoesNotAuthenticateUserResolvedOutsideRequestedTenant() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        OidcUser oidcUser = oidcUser("staff-01", null);
        User foreignUser = activeUser();
        foreignUser.setTenantId("tenant-foreign");
        when(userRepository.findWithDetailsByTenantAndIdentifier(
                "tenant-requested", "staff-01", "staff-01"))
                .thenReturn(Optional.of(foreignUser));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(oidcUser, null, List.of())
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "tenant-requested");

        new OidcPrincipalBridgeFilter(userRepository, "tenant-requested").doFilter(
                request,
                new MockHttpServletResponse(),
                (servletRequest, servletResponse) ->
                        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull()
        );
    }

    @Test
    void clearsAuthenticationWhenInternalUserIsNotActive() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        OidcUser oidcUser = oidcUser(null, " Invited@Example.Test ");
        User user = activeUser();
        user.setStatus("INVITED");
        when(userRepository.findWithDetailsByTenantAndIdentifier(
                "tenant-1", "invited@example.test", "invited@example.test"))
                .thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(oidcUser, null, List.of())
        );

        new OidcPrincipalBridgeFilter(userRepository, "tenant-1").doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (request, response) -> assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull()
        );
        verify(userRepository).findWithDetailsByTenantAndIdentifier("tenant-1", "invited@example.test", "invited@example.test");
    }

    @Test
    void clearsAuthenticationWhenOidcIdentityHasNoInternalAccount() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        OidcUser oidcUser = oidcUser("unknown", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(oidcUser, null, List.of())
        );

        when(userRepository.findWithDetailsByTenantAndIdentifier("tenant-1", "unknown", "unknown"))
                .thenReturn(Optional.empty());
        new OidcPrincipalBridgeFilter(userRepository, "tenant-1").doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (request, response) -> assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull()
        );
        verify(userRepository).findWithDetailsByTenantAndIdentifier("tenant-1", "unknown", "unknown");
    }

    @Test
    void rejectsClientTenantOverrideWhenVerifiedOidcClaimIsPresent() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        OidcUser oidcUser = oidcUser("staff-01", null);
        when(oidcUser.getClaims()).thenReturn(Map.of("tenant_id", "tenant-1"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(oidcUser, null, List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "tenant-foreign");

        new OidcPrincipalBridgeFilter(userRepository, "tenant-1").doFilter(
                request,
                new MockHttpServletResponse(),
                (servletRequest, servletResponse) -> assertThat(SecurityContextHolder.getContext().getAuthentication())
                        .isNull());

        verifyNoInteractions(userRepository);
    }

    private static OidcUser oidcUser(String preferredUsername, String email) {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getPreferredUsername()).thenReturn(preferredUsername);
        when(oidcUser.getEmail()).thenReturn(email);
        return oidcUser;
    }

    private static User activeUser() {
        Permission permission = new Permission();
        permission.setName("lms.quiz.read");
        Role role = new Role();
        role.setName("lms.teacher");
        role.setPermissions(Set.of(permission));
        User user = new User();
        user.setId("user-1");
        user.setUsername("staff-01");
        user.setEmail("staff@example.test");
        user.setFullName("Internal Staff");
        user.setStatus("ACTIVE");
        user.setTenantId("tenant-1");
        user.setAccountType("staff");
        user.setRoles(Set.of(role));
        return user;
    }
}
