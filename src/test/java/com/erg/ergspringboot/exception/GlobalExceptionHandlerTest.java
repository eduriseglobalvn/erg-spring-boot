package com.erg.ergspringboot.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.FieldErrorDetail;
import com.erg.ergspringboot.common.response.ApiResponse.TransportError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Set;
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
        assertThat(errorCode(response)).isEqualTo("RESOURCE_NOT_FOUND");
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
        assertThat(errorCode(response)).isEqualTo("INTERNAL_SERVER_ERROR");
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

    @Test
    void constraintViolationUsesValidationEnvelopeInsteadOfInternalServerError() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("X-Request-ID", "req-constraint");
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("listUsers.page");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be greater than or equal to 1");

        ResponseEntity<ApiResponse<Void>> response = handler.handleConstraintViolation(
                new ConstraintViolationException(Set.of(violation)),
                new ServletWebRequest(request));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("validation failed");
        assertThat(response.getBody().requestId()).isEqualTo("req-constraint");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/users");
        assertThat(errorFields(response)).singleElement()
                .isEqualTo(new FieldErrorDetail(
                        "listUsers.page", "ConstraintViolation", "ConstraintViolation",
                        "must be greater than or equal to 1"));
    }

    @Test
    void missingRequestParameterUsesStableBadRequestEnvelope() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/calendar/events");
        request.addHeader("X-Request-ID", "req-missing-param");

        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingRequestParameter(
                new org.springframework.web.bind.MissingServletRequestParameterException("from", "String"),
                new ServletWebRequest(request));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(errorCode(response)).isEqualTo("INVALID_REQUEST");
        assertThat(response.getBody().message()).isEqualTo("Missing required request parameter: from");
        assertThat(response.getBody().requestId()).isEqualTo("req-missing-param");
    }

    @Test
    void optimisticLockUsesStableConflictEnvelope() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/schools/school-1");
        request.addHeader("X-Request-ID", "req-stale-write");

        ResponseEntity<ApiResponse<Void>> response = handler.handleOptimisticLock(
                new org.springframework.orm.ObjectOptimisticLockingFailureException("School", "school-1"),
                new ServletWebRequest(request));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(errorCode(response)).isEqualTo("STALE_WRITE");
        assertThat(response.getBody().requestId()).isEqualTo("req-stale-write");
    }

    @Test
    void uniqueConstraintUsesStableConflictEnvelopeWithoutLeakingSql() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/lms/assignments");
        var exception = new org.springframework.dao.DataIntegrityViolationException(
                "duplicate key value violates unique constraint uq_secret_internal_name");

        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrity(
                exception, new ServletWebRequest(request));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(errorCode(response)).isEqualTo("DATA_CONFLICT");
        assertThat(response.getBody().message()).doesNotContain("uq_secret_internal_name");
    }

    @Test
    void idempotencyIndexViolationUsesDistinctConflictCodeWithoutLeakingSql() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/lms/assignments");
        request.addHeader("X-Request-ID", "req-idem-race");
        var exception = new org.springframework.dao.DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException(
                        "duplicate key value violates unique constraint \"uq_lms_assignments_tenant_idempotency\""));

        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrity(
                exception, new ServletWebRequest(request));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(errorCode(response)).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(response.getBody().requestId()).isEqualTo("req-idem-race");
        assertThat(response.getBody().message())
                .doesNotContain("uq_lms_assignments_tenant_idempotency")
                .contains("idempotency key");
    }

    @Test
    void otherConstraintOnAnInsertThatListsAnIdempotencyColumnIsNotAnIdempotencyConflict() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/lcms/quizzes/q1/publish");
        var exception = new org.springframework.dao.DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException(
                        "Batch entry 0 insert into quiz_versions (id,idempotency_key,is_active) values (...) was aborted: "
                                + "ERROR: duplicate key value violates unique constraint \"uq_quiz_versions_active\""));

        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrity(
                exception, new ServletWebRequest(request));

        assertThat(errorCode(response)).isEqualTo("DATA_CONFLICT");
    }

    @org.springframework.web.bind.annotation.RestController
    static class ProbeController {
        @org.springframework.web.bind.annotation.PostMapping(value = "/probe", consumes = "application/json")
        String post(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body) {
            return "ok";
        }

        @org.springframework.web.bind.annotation.GetMapping("/probe/{id}")
        String get(@org.springframework.web.bind.annotation.PathVariable int id,
                   @org.springframework.web.bind.annotation.RequestHeader("X-Probe") String probe) {
            return "ok";
        }

        @org.springframework.web.bind.annotation.GetMapping("/probe-date")
        String date() {
            return java.time.OffsetDateTime.parse("not-a-date").toString();
        }
    }

    @Test
    void mvcProtocolErrorsKeepTheirStatusInsideTheApiEnvelope() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new ProbeController()).setControllerAdvice(handler).build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/probe"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isMethodNotAllowed())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("Allow"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.statusCode").value(405));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/probe")
                        .contentType("text/plain").content("x"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnsupportedMediaType())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.statusCode").value(415));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/probe/abc").header("X-Probe", "1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.statusCode").value(400));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/probe/1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                        .value("Missing required request header: X-Probe"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/probe-date"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void oversizedUploadMapsToPayloadTooLarge() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMaxUploadSize(
                new org.springframework.web.multipart.MaxUploadSizeExceededException(1024),
                new ServletWebRequest(new MockHttpServletRequest("POST", "/api/v1/lcms/media/upload")));

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(errorCode(response)).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    private String errorCode(ResponseEntity<ApiResponse<Void>> response) {
        return ((TransportError.Code) response.getBody().errors()).value();
    }

    private java.util.List<FieldErrorDetail> errorFields(ResponseEntity<ApiResponse<Void>> response) {
        return ((TransportError.Fields) response.getBody().errors()).value();
    }
}
