package com.erg.ergspringboot.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsSuccessResponseWithMetadata() {
        ApiResponse<String> response = ApiResponse.success(
                200,
                "Loaded user",
                "user-1",
                "/api/v1/users/1",
                "req-123"
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.message()).isEqualTo("Loaded user");
        assertThat(response.data()).isEqualTo("user-1");
        assertThat(response.errors()).isNull();
        assertThat(response.path()).isEqualTo("/api/v1/users/1");
        assertThat(response.requestId()).isEqualTo("req-123");
        assertThat(response.timestamp()).isBeforeOrEqualTo(OffsetDateTime.now());
    }

    @Test
    void keepsRequestIdWireFormatSnakeCase() throws Exception {
        ApiResponse<String> response = ApiResponse.success(
                200,
                "Loaded user",
                "user-1",
                "/api/v1/users/1",
                "req-123"
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"request_id\":\"req-123\"");
        assertThat(json).doesNotContain("\"requestId\"");
    }

    @Test
    void buildsFailureResponseWithReusableErrorDetail() {
        ErrDetail error = new ErrDetail(
                "VALIDATION_ERROR",
                "Invalid request",
                List.of(new FieldErrorDetail("email", "email", "email", "must be a valid email"))
        );

        ApiResponse<Void> response = ApiResponse.failure(422, error, "/api/v1/users", "req-456");

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.message()).isEqualTo("Invalid request");
        assertThat(response.data()).isNull();
        assertThat(response.errors()).isEqualTo(error.details());
        assertThat(response.path()).isEqualTo("/api/v1/users");
        assertThat(response.requestId()).isEqualTo("req-456");
    }

    @Test
    void wrapsPaginatedDataLikeGo() {
        PaginatedData<String> data = PaginatedData.of("items", 51, 2, 20);

        assertThat(data.data()).isEqualTo("items");
        assertThat(data.total()).isEqualTo(51);
        assertThat(data.page()).isEqualTo(2);
        assertThat(data.limit()).isEqualTo(20);
        assertThat(data.totalPages()).isEqualTo(3);
    }
}
