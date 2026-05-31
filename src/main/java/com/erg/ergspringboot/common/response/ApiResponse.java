package com.erg.ergspringboot.common.response;

import java.time.OffsetDateTime;

public record ApiResponse<T>(
        int statusCode,
        String message,
        T data,
        Object errors,
        OffsetDateTime timestamp,
        String path,
        String request_id
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
