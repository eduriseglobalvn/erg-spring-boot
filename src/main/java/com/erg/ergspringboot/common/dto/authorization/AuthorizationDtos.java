package com.erg.ergspringboot.common.dto.authorization;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class AuthorizationDtos {

    private AuthorizationDtos() {
    }

    public record CreateRoleRequest(@NotBlank String name, String description, List<String> permissions) {
    }

    public record UpdateRoleRequest(String name, String description, List<String> permissions) {
    }

    public record RoleResponse(
            String id,
            String name,
            String description,
            List<String> permissions,
            @JsonProperty("is_default") boolean isDefault,
            OffsetDateTime createdAt,
            @JsonProperty("updated_at") OffsetDateTime updatedAt
    ) {
    }

    public record PermissionResponse(String id, String name, String group, String label, String description) {
    }

    public record PermissionGroupResponse(
            String id,
            String name,
            String label,
            int order,
            List<PermissionResponse> permissions
    ) {
    }

    public record AssignRolesRequest(@NotEmpty List<String> roleIds) {
    }

    public record BulkAssignRoleRequest(@NotEmpty List<String> userIds, @NotBlank String roleId) {
    }

    public record AddPermissionOverrideRequest(
            @JsonProperty("grant_type") @NotBlank String grantType,
            @NotBlank String permission,
            String reason,
            @JsonProperty("expires_at") OffsetDateTime expiresAt
    ) {
    }

    public record PermissionOverrideResponse(
            String id,
            @JsonProperty("user_id") String userId,
            String permission,
            @JsonProperty("grant_type") String grantType,
            String reason,
            @JsonProperty("expires_at") OffsetDateTime expiresAt,
            @JsonProperty("created_by") String createdBy,
            OffsetDateTime createdAt
    ) {
    }

    public record PreviewChangesRequest(Map<String, Object> changes) {
    }

    public record EffectivePermissionsResponse(
            @JsonProperty("user_id") String userId,
            @JsonProperty("role_permissions") List<String> rolePermissions,
            @JsonProperty("granted_permissions") List<String> grantedPermissions,
            @JsonProperty("denied_permissions") List<String> deniedPermissions,
            @JsonProperty("effective_permissions") List<String> effectivePermissions
    ) {
    }

    public record FeatureConfigResponse(
            @JsonProperty("user_id") String userId,
            List<String> permissions,
            Map<String, Boolean> features,
            FeatureAccessResponse featureAccess,
            List<String> roles
    ) {
    }

    public record FeatureAccessResponse(
            List<String> sidebar,
            List<String> dashboardWidgets,
            List<String> quickActions
    ) {
    }

    public record PreviewChangesResponse(
            @JsonProperty("current_permissions") List<String> currentPermissions,
            @JsonProperty("proposed_changes") Map<String, Object> proposedChanges,
            @JsonProperty("simulated_permissions") List<String> simulatedPermissions
    ) {
    }
}
