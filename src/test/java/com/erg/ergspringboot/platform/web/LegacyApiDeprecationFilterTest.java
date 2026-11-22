package com.erg.ergspringboot.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LegacyApiDeprecationFilterTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final LegacyApiDeprecationFilter filter = new LegacyApiDeprecationFilter(
            new LegacyApiRegistry(LocalDate.of(2026, 7, 22)), meters);

    @Test
    void marksLegacyAliasWithOwnerSunsetSuccessorAndMetric() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/content/history");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> response.setStatus(200);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("Deprecation")).isEqualTo("true");
        assertThat(response.getHeader("Sunset")).isEqualTo("Thu, 31 Dec 2026 23:59:59 GMT");
        assertThat(response.getHeader("Link"))
                .isEqualTo("</api/v1/content/history>; rel=\"successor-version\"");
        assertThat(response.getHeader("X-ERG-API-Owner")).isEqualTo("learningcontent");
        assertThat(meters.get("erg.api.deprecated.requests")
                .tag("route_group", "learning-content-legacy")
                .tag("owner", "learningcontent")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void leavesCanonicalRoutesUntouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/content/history");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> response.setStatus(200));

        assertThat(response.getHeader("Deprecation")).isNull();
        assertThat(meters.find("erg.api.deprecated.requests").counter()).isNull();
    }

    @Test
    void advertisesTheMountedContentSuccessor() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/content/items");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> response.setStatus(201));

        assertThat(response.getHeader("Deprecation")).isEqualTo("true");
        assertThat(response.getHeader("Link"))
                .isEqualTo("</api/v1/content/items>; rel=\"successor-version\"");
        assertThat(response.getHeader("X-ERG-API-Owner")).isEqualTo("learningcontent");
    }
}
