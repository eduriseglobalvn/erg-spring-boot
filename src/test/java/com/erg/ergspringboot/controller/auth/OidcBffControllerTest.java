package com.erg.ergspringboot.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.response.auth.AuthResponses.BffSessionResponse;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.util.List;
import java.util.Map;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.junit.jupiter.api.Test;

class OidcBffControllerTest {

    @Test
    void sanitizesPortalAndLocalReturnPath() {
        assertThat(OidcBffController.sanitizePortal("LCMS")).isEqualTo("lcms");
        assertThat(OidcBffController.sanitizePortal("google")).isEqualTo("lms");
        assertThat(OidcBffController.sanitizeReturnTo("/quiz/123?tab=edit")).isEqualTo("/quiz/123?tab=edit");
        assertThat(OidcBffController.sanitizeReturnTo("https://evil.example/steal")).isEqualTo("/");
        assertThat(OidcBffController.sanitizeReturnTo("//evil.example/steal")).isEqualTo("/");
    }

    @Test
    void projectsAuthenticatedMultiRoleSessionWithoutTokenMaterial() {
        ErgPrincipal principal = new ErgPrincipal(
                "user-1",
                "session-1",
                "erg",
                List.of("teacher", "student"),
                List.of("lms.assignment.read", "elearning.dashboard.read", "crm.lead.read"),
                List.of("lms.assignment.delete"),
                List.of("lms"),
                "erg",
                "user",
                Map.of(
                        "email", "user@erg.edu.vn",
                        "fullName", "User",
                        "accessToken", "should-not-leak",
                        "refreshToken", "should-not-leak",
                        "apiSecret", "should-not-leak"
                )
        );

        BffSessionResponse response = OidcBffController.toSessionResponse(principal);

        assertThat(response.tenantId()).isEqualTo("erg");
        assertThat(response.roles()).containsExactly("teacher", "student");
        assertThat(response.workspaces()).containsExactly("CRM", "STUDENT", "TEACHER");
        assertThat(response.portals()).containsExactly("crm", "elearning", "lms");
        assertThat(response.scope().fingerprint()).isNotBlank();
        assertThat(response.profile()).containsEntry("email", "user@erg.edu.vn");
        assertThat(response.profile()).doesNotContainKeys("accessToken", "refreshToken", "apiSecret", "token");
    }

    @Test
    void logoutInvalidatesOnlyTheBffHttpSession() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);

        var response = new OidcBffController().logout(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void legacyBffLoginEmitsSunsetSuccessorAndTelemetry() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        OidcBffController controller = new OidcBffController(
                Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC));
        controller.setMeterRegistry(meters);
        MockHttpSession session = new MockHttpSession();

        var response = controller.login("lcms", "/quiz/1", session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getFirst("Deprecation")).isEqualTo("true");
        assertThat(response.getHeaders().getFirst("Sunset")).isEqualTo("Sat, 31 Oct 2026 23:59:59 GMT");
        assertThat(response.getHeaders().getFirst("Link"))
                .isEqualTo("</admin>; rel=\"successor-version\"");
        assertThat(meters.get("erg.auth.legacy_workspace_alias.requests")
                .tag("alias", "lcms")
                .tag("workspace", "ADMIN")
                .counter().count()).isEqualTo(1);
        assertThat(session.getAttribute(OidcBffController.PORTAL_SESSION_KEY)).isEqualTo("lcms");
    }

    @Test
    void expiredLegacyBffLoginIsGoneAndDoesNotCreateAnAuthFlow() {
        OidcBffController controller = new OidcBffController(
                Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC));
        MockHttpSession session = new MockHttpSession();

        var response = controller.login("lcms", "/quiz/1", session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getHeaders().getFirst("Deprecation")).isEqualTo("true");
        assertThat(session.getAttribute(OidcBffController.PORTAL_SESSION_KEY)).isNull();
    }
}
