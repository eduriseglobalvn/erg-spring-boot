package com.erg.ergspringboot.exception;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.ErrDetail;
import com.erg.ergspringboot.platform.web.RequestCorrelation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Envelope builders for {@link GlobalExceptionHandler}; bodies never carry exception messages from infrastructure errors. */
final class ErrorResponses {

    /** Seconds a client should wait before retrying an overload response. */
    static final String RETRY_AFTER_SECONDS = "5";

    private ErrorResponses() {
    }

    static ResponseEntity<ApiResponse<Void>> invalidRequest(String path, String requestId) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "invalid request", path, requestId, false);
    }

    /** Pool/queue/lock/timeout exhaustion: the request did not fail on its merits, so tell the client to retry shortly. */
    static ResponseEntity<ApiResponse<Void>> unavailable(String path, String requestId) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "service temporarily unavailable, retry shortly", path, requestId, true);
    }

    static String protocolErrorCode(HttpStatus status) {
        return switch (status) {
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case UNSUPPORTED_MEDIA_TYPE -> "UNSUPPORTED_MEDIA_TYPE";
            case NOT_ACCEPTABLE -> "NOT_ACCEPTABLE";
            case NOT_FOUND -> "NOT_FOUND";
            case CONTENT_TOO_LARGE -> "PAYLOAD_TOO_LARGE";
            case SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE";
            default -> status.is5xxServerError() ? "SERVER_ERROR" : "INVALID_REQUEST";
        };
    }

    private static ResponseEntity<ApiResponse<Void>> build(
            HttpStatus status, String code, String message, String path, String requestId, boolean retryAfter) {
        ApiResponse<Void> response = ApiResponse.failure(status.value(), ErrDetail.of(code, message), path, requestId);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
                .header(RequestCorrelation.REQUEST_ID_HEADER, response.requestId());
        if (retryAfter) {
            builder.header("Retry-After", RETRY_AFTER_SECONDS);
        }
        return builder.body(response);
    }
}
