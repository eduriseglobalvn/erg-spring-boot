package com.erg.ergspringboot.common.dto.users;

import com.erg.ergspringboot.common.response.users.UserResponses.UserDetailResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.erg.ergspringboot.common.response.users.UserResponses.Meta;
import com.erg.ergspringboot.common.response.users.UserResponses.UserCreatedResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public final class EmployeeDtos {
    private EmployeeDtos() {
    }

    public record EmployeeProfileRequest(
            @Size(max = 100) String employeeCode,
            @Size(max = 36) String departmentId,
            @Size(max = 150) String jobTitle,
            @Size(max = 50) String employmentType,
            LocalDate joinedAt,
            LocalDate terminatedAt,
            @DecimalMin("0") BigDecimal experienceYears,
            @Size(max = 2000) String experienceSummary,
            @Size(max = 36) String managerId,
            @Size(max = 255) String workLocation,
            Map<String, Object> emergencyContact
    ) {
    }

    public record CreateEmployeeRequest(
            @NotBlank @Email String primaryEmail,
            @NotBlank @Size(min = 2, max = 100) String fullName,
            @Size(max = 20) String phone,
            @Email String recoveryEmail,
            @NotEmpty List<String> roleGroupIds,
            @NotEmpty @Valid List<EmployeeAccessPolicyRequest> policies,
            @NotBlank String credentialDelivery,
            @NotNull @Valid EmployeeProfileRequest profile
    ) {
    }

    public record EmployeeAccessPolicyRequest(
            @NotBlank String scopeType,
            @NotBlank String scopeId,
            @NotBlank String roleGroup
    ) {
    }

    public record EmployeeCreatedResponse(
            UserCreatedResponse account,
            EmployeeProfileResponse profile
    ) {
    }

    public record UpdateEmployeeRequest(
            @Size(min = 2, max = 100) String fullName,
            @Size(max = 20) String phone,
            @Valid EmployeeProfileRequest profile
    ) {
    }

    public record EmployeeProfileResponse(
            String id,
            String userId,
            String tenantId,
            String employeeCode,
            String departmentId,
            String jobTitle,
            String employmentType,
            LocalDate joinedAt,
            LocalDate terminatedAt,
            BigDecimal experienceYears,
            String experienceSummary,
            String managerId,
            String workLocation,
            Map<String, Object> emergencyContact
    ) {
    }

    public record EmployeeDetailResponse(
            UserDetailResponse account,
            EmployeeProfileResponse profile
    ) {
    }

    public record EmployeeListItemResponse(
            String id,
            String email,
            String fullName,
            @JsonProperty("avatar_url") String avatarUrl,
            String phone,
            String status,
            List<String> roles,
            @JsonProperty("last_login_at") String lastLoginAt,
            String createdAt,
            String employeeCode,
            String departmentId,
            String jobTitle,
            String employmentType,
            List<String> scopes,
            int activeDeviceCount,
            List<String> activePlatforms,
            String riskCode,
            String riskSeverity
    ) {
    }

    public record EmployeeListResponse(List<EmployeeListItemResponse> users, Meta meta) {
    }

    public record SecurityEventResponse(
            String id,
            String eventType,
            String severity,
            String portal,
            String platform,
            String result,
            String riskCode,
            String sessionId,
            String deviceId,
            String ipAddress,
            String reason,
            Map<String, Object> metadata,
            String occurredAt
    ) {
    }

    public record SecurityEventPageResponse(
            List<SecurityEventResponse> items,
            int page,
            int limit,
            long total,
            long totalPages
    ) {
    }

    public record SecurityFeedItemResponse(
            String id,
            String sourceType,
            String eventType,
            String tenantId,
            String userId,
            String userName,
            String userEmail,
            String severity,
            String portal,
            String platform,
            String result,
            String riskCode,
            String sessionId,
            String deviceId,
            String ipAddress,
            String reason,
            Map<String, Object> metadata,
            String occurredAt
    ) {
    }

    public record SecurityFeedPageResponse(
            List<SecurityFeedItemResponse> items,
            int page,
            int limit,
            long total,
            long totalPages
    ) {
    }

    public record RevokeEmployeeSessionRequest(@NotBlank String sessionId) {
    }
}
