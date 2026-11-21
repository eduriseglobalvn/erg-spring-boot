package com.erg.ergspringboot.common.response.sessions;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import com.erg.ergspringboot.common.response.users.UserResponses.AccountLifecycleResponse;

public final class SessionResponses {

    private SessionResponses() {
    }

    public record SessionContextResponse(
            @JsonProperty("session_id") String sessionId,
            @JsonProperty("user_id") String userId,
            @JsonProperty("tenant_id") String tenantId,
            @JsonProperty("device_id") String deviceId,
            @JsonProperty("ip_address") String ipAddress,
            boolean active,
            List<String> roles,
            String expiresAt,
            String lastActiveAt
    ) {
    }

    public record SessionBootstrapResponse(
            SessionBootstrapUser user,
            SessionBootstrapPermissions permissions,
            List<String> portals,
            AccountLifecycleResponse lifecycle,
            SessionBootstrapScope currentScope,
            List<SessionBootstrapUnit> manageableUnits,
            List<SessionBootstrapClass> manageableClasses,
            String tenantId,
            List<String> workspaces
    ) {
    }

    /** Identity only: GET /api/v1/sessions/me, no unit or class lists. */
    public record SessionIdentityResponse(
            SessionBootstrapUser user,
            String tenantId,
            SessionBootstrapPermissions permissions,
            List<String> portals,
            List<String> workspaces,
            AccountLifecycleResponse lifecycle
    ) {
    }

    /** GET /api/v1/sessions/scope: current scope plus one page of units and a bounded list of classes. */
    public record SessionScopeResponse(
            SessionBootstrapScope currentScope,
            SessionScopeUnits units,
            SessionScopeClasses classes,
            boolean truncated
    ) {
    }

    public record SessionScopeUnits(List<SessionBootstrapUnit> items, int page, int size, long totalItems, boolean hasNext) {
    }

    public record SessionScopeClasses(List<SessionBootstrapClass> items, boolean hasNext) {
    }

    public record SessionBootstrapUser(
            String id,
            String name,
            String email,
            String avatarUrl
    ) {
    }

    public record SessionBootstrapPermissions(
            /** True when manageableUnits is every school of the organisation (admin, Phòng đào tạo, director). */
            boolean allUnits,
            boolean canAccessGlobalErg,
            List<String> roles,
            List<String> grantedPermissions,
            List<String> deniedPermissions
    ) {
    }

    public record SessionBootstrapScope(
            String level,
            String unitId,
            String classId
    ) {
    }

    public record SessionBootstrapUnit(
            String id,
            String name,
            String avatar,
            String type,
            String level
    ) {
    }

    public record SessionBootstrapClass(
            String id,
            String name,
            String unitId,
            String grade,
            String academicYear,
            String status
    ) {
    }
}
