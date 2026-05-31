package com.erg.ergspringboot.platform.security;

import java.util.List;
import java.util.Map;

public record ErgPrincipal(
        String userId,
        String sessionId,
        String tenantId,
        List<String> roles,
        List<String> permissions,
        List<String> deniedPermissions,
        List<String> portals,
        String accountType,
        String accessLevel,
        Map<String, Object> attributes
) {

    public static ErgPrincipal systemAdmin() {
        return new ErgPrincipal(
                "system",
                "system-session",
                "erg",
                List.of("system.super_admin", "admin"),
                List.of("*"),
                List.of(),
                List.of("*"),
                "system",
                "super_admin",
                Map.of()
        );
    }
}
