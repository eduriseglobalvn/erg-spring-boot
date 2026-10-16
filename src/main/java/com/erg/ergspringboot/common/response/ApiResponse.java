package com.erg.ergspringboot.common.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import com.erg.ergspringboot.common.response.sync.SyncMutationResponses.ConflictPayload;

public record ApiResponse<T>(
        int statusCode,
        String message,
        T data,
        @Schema(implementation = Object.class)
        TransportError errors,
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
        TransportError errors = error.details().isEmpty()
                ? TransportError.code(error.code())
                : TransportError.fields(error.details());
        return new ApiResponse<>(statusCode, error.message(), null, errors, OffsetDateTime.now(), path, requestId);
    }

    public static <T> ApiResponse<T> conflict(int statusCode, String message, ConflictPayload conflict,
            String path, String requestId) {
        return new ApiResponse<>(statusCode, message, null, TransportError.conflict(conflict),
                OffsetDateTime.now(), path, requestId);
    }

    /** Typed Java model for the two established JSON shapes of the public {@code errors} field. */
    public sealed interface TransportError permits TransportError.Code, TransportError.Fields, TransportError.Conflict {

        record Code(@JsonValue String value) implements TransportError {
            public Code {
                if (value == null || value.isBlank()) {
                    throw new IllegalArgumentException("error code must not be blank");
                }
            }
        }

        record Fields(@JsonValue List<FieldErrorDetail> value) implements TransportError {
            public Fields {
                value = List.copyOf(value);
            }
        }

        record Conflict(@JsonValue ConflictPayload value) implements TransportError {
            public Conflict {
                if (value == null) throw new IllegalArgumentException("conflict payload must not be null");
            }
        }

        static TransportError code(String code) {
            return new Code(code);
        }

        static TransportError fields(List<FieldErrorDetail> fields) {
            return new Fields(fields);
        }

        static TransportError conflict(ConflictPayload conflict) {
            return new Conflict(conflict);
        }
    }
}
