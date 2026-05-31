package com.erg.ergspringboot.common.request.users;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public final class UserRequests {

    private UserRequests() {
    }

    public record OnboardingRequest(
            @NotBlank @Size(min = 2, max = 100) String fullName,
            @Size(max = 20) String phone,
            @Size(max = 500) String bio,
            String gender,
            @JsonProperty("date_of_birth") String dateOfBirth,
            @Size(max = 255) String address,
            @Size(max = 100) String city,
            @Size(max = 100) String district,
            @JsonProperty("job_title") @Size(max = 100) String jobTitle,
            @Size(max = 100) String region
    ) {
    }

    public record ChangePasswordRequest(
            @JsonProperty("old_password") @NotBlank String oldPassword,
            @JsonProperty("new_password") @NotBlank @Size(min = 8, max = 128) String newPassword
    ) {
    }

    public record UpdateProfileRequest(
            @JsonProperty("full_name") @Size(min = 2, max = 100) String fullName,
            @Size(max = 20) String phone,
            @Size(max = 500) String bio,
            String gender,
            @JsonProperty("date_of_birth") String dateOfBirth,
            @Size(max = 255) String address,
            @Size(max = 100) String city,
            @Size(max = 100) String district,
            @JsonProperty("job_title") @Size(max = 100) String jobTitle,
            @Size(max = 100) String region,
            @JsonProperty("social_links") Map<String, String> socialLinks,
            @JsonProperty("avatar_url") String avatarUrl
    ) {
    }

    public record AdminUpdateUserStatusRequest(@NotBlank String status) {
    }

    public record AdminCreateUserRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password,
            @NotBlank @Size(min = 2, max = 100) String fullName,
            List<String> roles,
            String phone
    ) {
    }

    public record BulkStatusRequest(
            @JsonProperty("user_ids") @NotEmpty List<String> userIds,
            @NotBlank String status
    ) {
    }

    public record BulkDeleteRequest(
            @JsonProperty("user_ids") @NotEmpty List<String> userIds
    ) {
    }

    public record AdminAssignRolesRequest(
            @NotEmpty List<String> roles
    ) {
    }

    public record AdminAssignPermissionsRequest(
            @NotEmpty List<String> permissions,
            @JsonProperty("grant_type") @NotBlank String grantType,
            @Size(max = 255) String reason,
            @JsonProperty("expires_at") String expiresAt
    ) {
    }

    public record ListUsersQuery(
            Integer page,
            Integer limit,
            String search,
            String status,
            String role
    ) {
    }
}
