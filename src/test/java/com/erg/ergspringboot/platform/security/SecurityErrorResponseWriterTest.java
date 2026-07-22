package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class SecurityErrorResponseWriterTest {

    private final SecurityErrorResponseWriter writer = new SecurityErrorResponseWriter(
            JsonMapper.builder().findAndAddModules().build());

    @Test
    void authenticationFailureUsesTheSameRequestIdInHeaderAndBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/lms/assignments");
        request.setAttribute("requestId", "req-security");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request, response, 401, "AUTHENTICATION_REQUIRED", "Authentication is required");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("X-Request-ID")).isEqualTo("req-security");
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString())
                .contains("\"statusCode\":401")
                .contains("\"errors\":\"AUTHENTICATION_REQUIRED\"")
                .contains("\"request_id\":\"req-security\"")
                .doesNotContain("stackTrace");
    }
}
