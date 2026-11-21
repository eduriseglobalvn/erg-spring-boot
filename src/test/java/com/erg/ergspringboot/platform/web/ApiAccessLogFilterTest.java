package com.erg.ergspringboot.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.platform.security.ErgPrincipal;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(OutputCaptureExtension.class)
class ApiAccessLogFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // Successful calls log at DEBUG now, so the masking assertions use an INFO-level (4xx) response.
    private static MockHttpServletResponse rejected() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(400);
        return response;
    }

    @Test
    void masksSensitiveQueryStringValues(CapturedOutput output) throws ServletException, IOException {
        ApiAccessLogFilter filter = new ApiAccessLogFilter(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");
        request.setQueryString(
                "page=1&access_token=secret-token&password=hunter2&api_key=abc123&Api%2DKey=encoded-secret"
        );

        filter.doFilter(request, rejected(), new MockFilterChain());

        assertThat(output)
                .contains("/api/v1/users?page=1&access_token=***&password=***&api_key=***&Api%2DKey=***");
        assertThat(output)
                .doesNotContain("secret-token")
                .doesNotContain("hunter2")
                .doesNotContain("abc123")
                .doesNotContain("encoded-secret");
    }

    @Test
    void leavesQueryStringIntactWhenSensitiveMaskingDisabled(CapturedOutput output) throws ServletException, IOException {
        ApiAccessLogFilter filter = new ApiAccessLogFilter(false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");
        request.setQueryString("access_token=secret-token&password=hunter2");

        filter.doFilter(request, rejected(), new MockFilterChain());

        assertThat(output).contains("/api/v1/users?access_token=secret-token&password=hunter2");
    }

    @Test
    void prefersPrincipalIdentityOverSpoofableHeaders(CapturedOutput output) throws ServletException, IOException {
        ApiAccessLogFilter filter = new ApiAccessLogFilter(true);
        ErgPrincipal principal = new ErgPrincipal(
                "principal-user",
                "session-1",
                "principal-tenant",
                List.of("teacher"),
                List.of(),
                List.of(),
                List.of("lms"),
                "staff",
                "teacher",
                Map.of()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/health");
        request.addHeader("X-User-ID", "header-user");
        request.addHeader("X-Tenant-ID", "header-tenant");

        filter.doFilter(request, rejected(), new MockFilterChain());

        assertThat(output).contains("tenantId=principal-tenant").contains("userId=principal-user");
        assertThat(output).doesNotContain("tenantId=header-tenant").doesNotContain("userId=header-user");
    }

    @Test
    void fastSuccessfulReadsAreNotLoggedAtInfo(CapturedOutput output) throws ServletException, IOException {
        ApiAccessLogFilter filter = new ApiAccessLogFilter(true);

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/users"), new MockHttpServletResponse(), new MockFilterChain());
        MockHttpServletResponse failed = new MockHttpServletResponse();
        failed.setStatus(404);
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/missing"), failed, new MockFilterChain());

        assertThat(output).doesNotContain("/api/v1/users").contains("api GET /api/v1/missing -> 404");
    }

    @Test
    void successfulWritesAreNotLoggedAtInfo(CapturedOutput output) throws ServletException, IOException {
        new ApiAccessLogFilter(true).doFilter(
                new MockHttpServletRequest("POST", "/api/v1/quiet"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(output).doesNotContain("/api/v1/quiet");
    }
}
