package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.controller.sessions.SessionController;
import com.erg.ergspringboot.exception.UnauthorizedException;
import com.erg.ergspringboot.service.sessions.SessionService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.file.Files;
import java.nio.file.Path;

@ExtendWith(MockitoExtension.class)
class SecurityHardeningTest {

    @Mock
    private ErgPrincipalService principalService;

    @Mock
    private SessionService sessionService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void defaultModeIgnoresSpoofedIdentityRolePermissionHeaders() throws Exception {
        ErgAuthenticationFilter filter = new ErgAuthenticationFilter(principalService, new ErgSecurityProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/platform/tenancy");
        request.addHeader("X-User-ID", "attacker");
        request.addHeader("X-Roles", "admin");
        request.addHeader("X-Permissions", "*");
        request.addHeader("X-Managed-EducationUnit-IDs", "*");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(principalService, never()).fromBearerToken("*", "erg");
    }

    @Test
    void accessContextUsesVerifiedPrincipalForIdentityAndManagedScope() {
        ErgPrincipal principal = new ErgPrincipal(
                "verified-user",
                "verified-session",
                "verified-tenant",
                List.of("teacher"),
                List.of("lms.unit.read"),
                List.of(),
                List.of("lms"),
                "erg",
                "user",
                Map.of(
                        "managedEducationUnitIds", List.of("unit-1"),
                        "managedClassIds", List.of("class-1")
                )
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/lms/centers");
        request.addHeader("X-User-ID", "attacker");
        request.addHeader("X-Tenant-ID", "spoof-tenant");
        request.addHeader("X-Managed-EducationUnit-IDs", "*");
        request.addHeader("X-Managed-Class-IDs", "*");
        request.addHeader("X-Current-EducationUnit-ID", "unit-1");

        UserAccessContext access = UserAccessContext.from(request);

        assertThat(access.userId()).isEqualTo("verified-user");
        assertThat(access.tenantId()).isEqualTo("verified-tenant");
        assertThat(access.managedEducationUnitIds()).containsExactly("unit-1");
        assertThat(access.managedClassIds()).containsExactly("class-1");
        assertThat(access.canAccessEducationUnit("unit-2")).isFalse();
        assertThat(access.canAccessClass("class-2")).isFalse();
    }

    @Test
    void accessContextClampsCurrentScopeHeadersToVerifiedManagedScope() {
        ErgPrincipal principal = new ErgPrincipal(
                "verified-user",
                "verified-session",
                "verified-tenant",
                List.of("teacher"),
                List.of("lms.unit.read"),
                List.of(),
                List.of("lms"),
                "erg",
                "user",
                Map.of(
                        "managedEducationUnitIds", List.of("unit-1", "unit-2"),
                        "managedClassIds", List.of("class-1", "class-2")
                )
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/lms/classes");
        request.addHeader("X-Current-EducationUnit-ID", "unit-outside");
        request.addHeader("X-Current-Class-ID", "class-outside");

        UserAccessContext access = UserAccessContext.from(request);

        assertThat(access.currentEducationUnitId()).isEqualTo("unit-1");
        assertThat(access.currentClassId()).isEqualTo("class-1");
        assertThat(access.canAccessEducationUnit("unit-outside")).isFalse();
        assertThat(access.canAccessClass("class-outside")).isFalse();
    }

    @Test
    void bearerAuthenticationUsesVerifiedPrincipalAndIgnoresSpoofedHeaders() throws Exception {
        ErgAuthenticationFilter filter = new ErgAuthenticationFilter(principalService, new ErgSecurityProperties());
        ErgPrincipal verifiedPrincipal = new ErgPrincipal(
                "verified-user",
                "verified-session",
                "verified-tenant",
                List.of("teacher"),
                List.of("lms.unit.read"),
                List.of(),
                List.of("lms"),
                "erg",
                "user",
                Map.of("managedEducationUnitIds", List.of("unit-1"))
        );
        when(principalService.fromBearerToken("valid-token", "spoof-tenant"))
                .thenReturn(Optional.of(verifiedPrincipal));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/lms/centers");
        request.addHeader("Authorization", "Bearer valid-token");
        request.addHeader("X-User-ID", "attacker");
        request.addHeader("X-Tenant-ID", "spoof-tenant");
        request.addHeader("X-Roles", "admin");
        request.addHeader("X-Permissions", "*");
        request.addHeader("X-Managed-EducationUnit-IDs", "*");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(verifiedPrincipal);
    }

    @Test
    void bffModeRejectsBearerTransportInsteadOfMixingTokenAndCookieSessions() throws Exception {
        ErgAuthenticationFilter filter = new ErgAuthenticationFilter(
                principalService, new ErgSecurityProperties(), true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/lms/classes");
        request.addHeader("Authorization", "Bearer legacy-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        verify(principalService, never()).fromBearerToken("legacy-token", "erg");
    }

    @Test
    void bffSecurityChainUsesCookieCsrfAndSessionIfRequired() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java"));

        assertThat(source)
                .contains("CookieCsrfTokenRepository.withHttpOnlyFalse()")
                .contains("SessionCreationPolicy.IF_REQUIRED")
                .contains("csrfTokenRepository(csrfRepository)")
                .contains("setCookieCustomizer")
                .contains("sameSite(\"Lax\")")
                .contains("secure(true)")
                .contains("oauth2Login");
    }

    @Test
    void bearerAuthenticationWithoutTenantDoesNotAttemptDefaultTenantLookup() throws Exception {
        ErgAuthenticationFilter filter = new ErgAuthenticationFilter(principalService, new ErgSecurityProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/lms/classes");
        request.addHeader("Authorization", "Bearer tenantless-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute("erg.auth.failureCode")).isEqualTo("AUTHENTICATION_REQUIRED");
        verify(principalService, never()).fromBearerToken("tenantless-token", "erg");
        verify(principalService, never()).fromBearerToken("tenantless-token", null);
    }

    @Test
    void bearerAuthenticationScopesTenantContextToPrincipalLookupAndRestoresIt() throws Exception {
        ErgAuthenticationFilter filter = new ErgAuthenticationFilter(principalService, new ErgSecurityProperties());
        TenantContext previous = TenantContext.of("outer-tenant", "outer-user", "admin");
        TenantContext.set(previous);
        ErgPrincipal verifiedPrincipal = new ErgPrincipal(
                "verified-user", "verified-session", "tenant-a", List.of("user"),
                List.of("profile.self"), List.of(), List.of("lms"), "erg", "user", Map.of()
        );
        when(principalService.fromBearerToken("valid-token", "tenant-a")).thenAnswer(invocation -> {
            assertThat(TenantContext.current()).isNotNull();
            assertThat(TenantContext.current().tenantId()).isEqualTo("tenant-a");
            assertThat(TenantContext.current().userId()).isNull();
            return Optional.of(verifiedPrincipal);
        });
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/lms/classes");
        request.addHeader("Authorization", "Bearer valid-token");
        request.addHeader("X-Tenant-ID", "tenant-a");

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                assertThat(TenantContext.current()).isSameAs(previous));

        assertThat(TenantContext.current()).isSameAs(previous);
    }

    @Test
    void bearerAuthenticationRestoresTenantContextWhenPrincipalLookupFails() {
        ErgAuthenticationFilter filter = new ErgAuthenticationFilter(principalService, new ErgSecurityProperties());
        TenantContext previous = TenantContext.of("outer-tenant", "outer-user", "admin");
        TenantContext.set(previous);
        when(principalService.fromBearerToken("broken-token", "tenant-a")).thenAnswer(invocation -> {
            assertThat(TenantContext.current()).isNotNull();
            assertThat(TenantContext.current().tenantId()).isEqualTo("tenant-a");
            throw new IllegalStateException("lookup failed");
        });
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/lms/classes");
        request.addHeader("Authorization", "Bearer broken-token");
        request.addHeader("X-Tenant-ID", "tenant-a");

        assertThatThrownBy(() -> filter.doFilter(
                request, new MockHttpServletResponse(), new MockFilterChain()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("lookup failed");
        assertThat(TenantContext.current()).isSameAs(previous);
    }

    @Test
    void replacedBearerSessionMarksRequestWithSpecificAuthenticationFailure() throws Exception {
        ErgAuthenticationFilter filter = new ErgAuthenticationFilter(principalService, new ErgSecurityProperties());
        when(principalService.fromBearerToken("replaced-token", "erg")).thenAnswer(invocation -> {
            assertThat(TenantContext.current().tenantId()).isEqualTo("erg");
            return Optional.empty();
        });
        when(principalService.authenticationFailureCode("replaced-token", "erg")).thenAnswer(invocation -> {
            assertThat(TenantContext.current().tenantId()).isEqualTo("erg");
            return Optional.of("AUTH_SESSION_REPLACED");
        });
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer replaced-token");
        request.addHeader("X-Tenant-ID", "erg");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getAttribute("erg.auth.failureCode")).isEqualTo("AUTH_SESSION_REPLACED");
        assertThat(TenantContext.current()).isNull();
    }

    @Test
    void firstLoginRestrictedPrincipalCannotCallLmsApis() throws Exception {
        ErgAuthenticationFilter filter = new ErgAuthenticationFilter(principalService, new ErgSecurityProperties());
        ErgPrincipal restricted = new ErgPrincipal(
                "teacher-1", "session-1", "erg", List.of("lms_teacher_standard"),
                List.of("lms.dashboard.read", "profile.self"), List.of(), List.of("lms"),
                "erg", "user", Map.of("lifecycleRestricted", true)
        );
        when(principalService.fromBearerToken("restricted-token", "erg")).thenReturn(Optional.of(restricted));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/lms/classes");
        request.addHeader("Authorization", "Bearer restricted-token");
        request.addHeader("X-Tenant-ID", "erg");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void lmsAndCalendarOperationsUseSeededActionPermissions() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java"));
        String calendarPolicies;
        String lmsPolicies;
        try (var files = Files.list(Path.of(
                "src/main/java/com/erg/ergspringboot/calendar/authorization"));
             var lmsFiles = Files.list(Path.of("src/main/java/com/erg/ergspringboot/lms/authorization"))) {
            calendarPolicies = files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (java.io.IOException exception) {
                            throw new java.io.UncheckedIOException(exception);
                        }
                    })
                    .collect(java.util.stream.Collectors.joining("\n"));
            lmsPolicies = lmsFiles.filter(path -> path.toString().endsWith(".java"))
                    .map(SecurityHardeningTest::readUnchecked)
                    .collect(java.util.stream.Collectors.joining("\n"));
        }

        assertThat(source).doesNotContain("\"lms.schedule.manage\"");
        assertThat(source).doesNotContain("\"lms.unit.read\"");
        assertThat(source + lmsPolicies).contains(
                "account.self.read",
                "account.self.update",
                "account.self.recovery_email.update",
                "account.self.password.change",
                "account.self.sessions.read",
                "lms.assignment.update",
                "lms.assignment.delete",
                "lms.assignment.assign",
                "lms.student_group.create",
                "lms.student_group.update",
                "lms.student_group.delete"
        );
        assertThat(calendarPolicies).contains(
                "lms.schedule.create",
                "lms.schedule.update",
                "lms.schedule.delete",
                "lms.schedule.publish",
                "lms.schedule.import"
        );
    }

    @Test
    void teacherLmsCapabilitiesUseTheirCatalogPermissionsOnRealEndpoints() throws Exception {
        String securitySource = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java"));
        String graphQlSource = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/controller/lms/graphql/LmsHomeworkWorkspaceGraphQlController.java"));
        String lcmsGraphQlSource = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/controller/lcms/graphql/LcmsWorkspaceGraphQlController.java"));
        String lmsPolicySource;
        String lcmsPolicySource;
        try (var lmsFiles = Files.list(Path.of("src/main/java/com/erg/ergspringboot/lms/authorization"));
             var lcmsFiles = Files.list(Path.of("src/main/java/com/erg/ergspringboot/lcms/authorization"))) {
            lmsPolicySource = lmsFiles.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> readUnchecked(path)).collect(java.util.stream.Collectors.joining("\n"));
            lcmsPolicySource = lcmsFiles.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> readUnchecked(path)).collect(java.util.stream.Collectors.joining("\n"));
        }

        assertThat(securitySource + lmsPolicySource + lcmsPolicySource).contains(
                "lms.assignment.progress.read",
                "lms.exercise.create",
                "lms.exercise.update",
                "lms.exercise.delete",
                "lms.resource.create",
                "lms.resource.update",
                "lms.resource.delete",
                "lms.resource.share",
                "lms.notification.read",
                "lms.notification.preferences.update"
        );
        assertThat(graphQlSource).contains("lms.assignment.progress.read");
        assertThat(lcmsGraphQlSource).contains("lms.exercise.read");
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    @Test
    void recoveryEmailLifecycleWritesAuditableEventsWithoutRawOtp() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/auth/AccountVerificationService.java"));

        assertThat(source).contains("RECOVERY_EMAIL_CHALLENGE_SENT", "RECOVERY_EMAIL_VERIFIED");
        assertThat(source).doesNotContain("\"otp\"");
        assertThat(source).doesNotContain("rawCode, \"before\"");
    }

    @Test
    void emptyManagedScopeDeniesByDefault() {
        ErgPrincipal principal = new ErgPrincipal(
                "verified-user",
                "verified-session",
                "verified-tenant",
                List.of("teacher"),
                List.of("lms.unit.read"),
                List.of(),
                List.of("lms"),
                "erg",
                "user",
                Map.of()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );

        UserAccessContext access = UserAccessContext.from(new MockHttpServletRequest("GET", "/api/v1/lms/centers"));

        assertThat(access.managedEducationUnitIds()).isEmpty();
        assertThat(access.managedClassIds()).isEmpty();
        assertThat(access.canAccessEducationUnit("unit-1")).isFalse();
        assertThat(access.canAccessClass("class-1")).isFalse();
    }

    @Test
    void currentSessionRejectsSpoofedSessionIdHeader() {
        authenticateVerifiedSession();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/sessions/context");
        request.addHeader("X-Session-ID", "spoof-session");

        assertThatThrownBy(() -> new SessionController(sessionService).currentSession(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("session id does not match");
    }

    @Test
    void currentSessionRejectsSpoofedTenantIdHeader() {
        authenticateVerifiedSession();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/sessions/context");
        request.addHeader("X-Tenant-ID", "spoof-tenant");

        assertThatThrownBy(() -> new SessionController(sessionService).currentSession(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("tenant id does not match");
    }

    @Test
    void currentSessionDoesNotTrustTenantHeaderWithoutVerifiedPrincipal() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/sessions/context");
        request.addHeader("X-Session-ID", "client-session");
        request.addHeader("X-Tenant-ID", "client-tenant");

        assertThatThrownBy(() -> new SessionController(sessionService).currentSession(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("verified principal");
    }

    @Test
    void authenticatedBusinessTenantResolversNeverFallbackToErg() throws Exception {
        List<Path> roots = List.of(
                Path.of("src/main/java/com/erg/ergspringboot/platform/graphql"),
                Path.of("src/main/java/com/erg/ergspringboot/controller/calendar/rest"),
                Path.of("src/main/java/com/erg/ergspringboot/controller/sessions"),
                Path.of("src/main/java/com/erg/ergspringboot/lms/internal"));
        List<Path> offenders = new ArrayList<>();
        for (Path root : roots) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                    if (Files.readString(path).contains("? \"erg\"")) offenders.add(path);
                }
            }
        }
        assertThat(offenders).isEmpty();
    }

    private void authenticateVerifiedSession() {
        ErgPrincipal principal = new ErgPrincipal(
                "verified-user",
                "verified-session",
                "verified-tenant",
                List.of("user"),
                List.of("profile.self"),
                List.of(),
                List.of("*"),
                "erg",
                "user",
                Map.of()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
    }
}
