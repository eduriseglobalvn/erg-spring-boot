package com.erg.ergspringboot.common.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record ApiResponse<T>(
        int statusCode,
        String message,
        T data,
        Object errors,
        OffsetDateTime timestamp,
        String path,
        @JsonProperty("request_id")
        String requestId
) {

    public static <T> ApiResponse<T> success(
            int statusCode,
            String message,
            T data,
            String path,
            String requestId
    ) {
        return new ApiResponse<>(statusCode, message, data, null, OffsetDateTime.now(), path, requestId);
    }

    public static <T> ApiResponse<T> failure(int statusCode, ErrDetail error, String path, String requestId) {
        Object errors = error.details().isEmpty() ? error.code() : error.details();
        return new ApiResponse<>(statusCode, error.message(), null, errors, OffsetDateTime.now(), path, requestId);
    }
}
