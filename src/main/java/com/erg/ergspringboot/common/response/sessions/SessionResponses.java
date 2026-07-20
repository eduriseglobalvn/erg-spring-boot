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
            List<SessionBootstrapClass> manageableClasses
    ) {
    }

    public record SessionBootstrapUser(
            String id,
            String name,
            String email,
            String avatarUrl
    ) {
    }

    public record SessionBootstrapPermissions(
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
