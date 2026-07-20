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
    void replacedBearerSessionMarksRequestWithSpecificAuthenticationFailure() throws Exception {
        ErgAuthenticationFilter filter = new ErgAuthenticationFilter(principalService, new ErgSecurityProperties());
        when(principalService.fromBearerToken("replaced-token", "erg")).thenReturn(Optional.empty());
        when(principalService.authenticationFailureCode("replaced-token", "erg"))
                .thenReturn(Optional.of("AUTH_SESSION_REPLACED"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer replaced-token");
        request.addHeader("X-Tenant-ID", "erg");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getAttribute("erg.auth.failureCode")).isEqualTo("AUTH_SESSION_REPLACED");
    }

    @Test
    void firstLoginRestrictedPrincipalCannotCallLmsApis() throws Exception {
        ErgAuthenticationFilter filter = new ErgAuthenticationFilter(principalService, new ErgSecurityProperties());
        ErgPrincipal restricted = new ErgPrincipal(
                "teacher-1", "session-1", "erg", List.of("lms_teacher_standard"),
                List.of("lms.dashboard.read", "profile.self"), List.of(), List.of("lms"),
                "erg", "user", Map.of("lifecycleRestricted", true)
        );
        when(principalService.fromBearerToken("restricted-token", null)).thenReturn(Optional.of(restricted));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/lms/classes");
        request.addHeader("Authorization", "Bearer restricted-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void lmsCommandRoutesUseSeededActionPermissions() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java"));

        assertThat(source).doesNotContain("\"lms.schedule.manage\"");
        assertThat(source).doesNotContain("\"lms.unit.read\"");
        assertThat(source).contains(
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
                "lms.student_group.delete",
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

        assertThat(securitySource).contains(
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
