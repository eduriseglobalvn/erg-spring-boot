package com.erg.ergspringboot.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesResourceNotFoundException() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/1");
        request.addHeader("X-Request-Id", "req-not-found");

        ResponseEntity<ApiResponse<Void>> response = handler.handleApiException(
                new ResourceNotFoundException("User not found"),
                new ServletWebRequest(request)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getHeaders().getFirst("X-Request-ID")).isEqualTo("req-not-found");
        assertThat(response.getBody().statusCode()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("User not found");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/users/1");
        assertThat(response.getBody().requestId()).isEqualTo("req-not-found");
        assertThat(response.getBody().errors()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void handlesUnexpectedException() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");

        ResponseEntity<ApiResponse<Void>> response = handler.handleUnexpectedException(
                new RuntimeException("Database is down"),
                new ServletWebRequest(request)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().statusCode()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("Unexpected server error");
        assertThat(response.getBody().errors()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/orders");
    }

    @Test
    void rateLimitedAuthenticationResponseIncludesRetryAfter() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        ResponseEntity<ApiResponse<Void>> response = handler.handleApiException(
                new com.erg.ergspringboot.exception.auth.AuthFailure.TooManyAttempts(),
                new ServletWebRequest(request));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
    }
}
