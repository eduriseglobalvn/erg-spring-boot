package com.erg.ergspringboot.exception;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.ErrDetail;
import com.erg.ergspringboot.common.response.FieldErrorDetail;
import com.erg.ergspringboot.platform.web.RequestIdFilter;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception,
            WebRequest request
    ) {
        List<FieldErrorDetail> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .sorted(Comparator.comparing(FieldError::getField).thenComparing(this::validationTag))
                .map(error -> new FieldErrorDetail(
                        error.getField(),
                        validationTag(error),
                        validationTag(error),
                        error.getDefaultMessage()
                ))
                .toList();
        ApiResponse<Void> response = ApiResponse.failure(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                new ErrDetail("VALIDATION_ERROR", "validation failed", details),
                getRequestPath(request),
                getRequestId(request)
        );

        return ResponseEntity.unprocessableEntity()
                .header("X-Request-ID", response.requestId())
                .body(response);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(
            org.springframework.http.converter.HttpMessageNotReadableException exception,
            WebRequest request
    ) {
        ApiResponse<Void> response = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                "invalid JSON body",
                null,
                "INVALID_REQUEST",
                java.time.OffsetDateTime.now(),
                getRequestPath(request),
                getRequestId(request)
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("X-Request-ID", response.requestId())
                .body(response);
    }

    @ExceptionHandler(com.erg.ergspringboot.service.quiz.publish.QuizPublishService.ValidationFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleQuizValidationFailed(
            com.erg.ergspringboot.service.quiz.publish.QuizPublishService.ValidationFailedException exception,
            WebRequest request
    ) {
        List<FieldErrorDetail> details = exception.errors().stream()
                .map(error -> new FieldErrorDetail(error.path(), error.code(), error.code(), error.message()))
                .toList();
        ApiResponse<Void> response = ApiResponse.failure(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                new ErrDetail(exception.getCode(), exception.getMessage(), details),
                getRequestPath(request),
                getRequestId(request)
        );
        return ResponseEntity.unprocessableEntity()
                .header("X-Request-ID", response.requestId())
                .body(response);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception, WebRequest request) {
        ApiResponse<Void> response = buildErrorResponse(
                exception.getStatus(),
                exception.getTitle(),
                exception.getMessage(),
                exception.getCode(),
                request
        );

        var builder = ResponseEntity.status(exception.getStatus())
                .header("X-Request-ID", response.requestId());
        if ("AUTH_TOO_MANY_ATTEMPTS".equals(exception.getCode())) {
            builder.header("Retry-After", "60");
        }
        return builder.body(response);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException exception,
            WebRequest request
    ) {
        ApiResponse<Void> response = buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                "You do not have permission to access this resource",
                "ACCESS_DENIED",
                request
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .header("X-Request-ID", response.requestId())
                .body(response);
    }

    @ExceptionHandler(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingCredentials(
            org.springframework.security.authentication.AuthenticationCredentialsNotFoundException exception,
            WebRequest request
    ) {
        ApiResponse<Void> response = buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                "Authentication is required",
                "AUTHENTICATION_REQUIRED",
                request
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header("X-Request-ID", response.requestId())
                .body(response);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            org.springframework.web.servlet.resource.NoResourceFoundException exception,
            WebRequest request
    ) {
        ApiResponse<Void> response = buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "Not Found",
                "resource not found",
                "NOT_FOUND",
                request
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header("X-Request-ID", response.requestId())
                .body(response);
    }

    @ExceptionHandler(org.springframework.web.servlet.NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFound(
            org.springframework.web.servlet.NoHandlerFoundException exception,
            WebRequest request
    ) {
        ApiResponse<Void> response = buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "Not Found",
                "resource not found",
                "NOT_FOUND",
                request
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header("X-Request-ID", response.requestId())
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception, WebRequest request) {
        ApiResponse<Void> response = buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "Unexpected server error",
                "INTERNAL_SERVER_ERROR",
                request
        );
        LOGGER.error("Unhandled API exception [requestId={}]", response.requestId(), exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("X-Request-ID", response.requestId())
                .body(response);
    }

    private ApiResponse<Void> buildErrorResponse(
            HttpStatus status,
            String title,
            String detail,
            String code,
            WebRequest request
    ) {
        ErrDetail error = ErrDetail.of(code, detail);
        return ApiResponse.failure(status.value(), error, getRequestPath(request), getRequestId(request));
    }

    private String getRequestPath(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest().getRequestURI();
        }

        return null;
    }

    private String getRequestId(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            Object attribute = servletWebRequest.getRequest().getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
            if (attribute instanceof String requestId && !requestId.isBlank()) {
                return requestId;
            }
        }

        String requestId = request.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        return requestId == null || requestId.isBlank() ? null : requestId;
    }

    private String validationTag(FieldError error) {
        String code = error.getCode();
        return code == null || code.isBlank() ? "Invalid" : code;
    }
}
