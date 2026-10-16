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

    public ErgPrincipal {
        roles = immutableList(roles);
        permissions = immutableList(permissions);
        deniedPermissions = immutableList(deniedPermissions);
        portals = immutableList(portals);
        attributes = immutableAttributes(attributes);
    }

    public static ErgPrincipal authenticated(
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
        return new ErgPrincipal(userId, sessionId, tenantId, roles, permissions, deniedPermissions,
                portals, accountType, accessLevel, attributes);
    }

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

    private static List<String> immutableList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static Map<String, Object> immutableAttributes(Map<String, Object> values) {
        if (values == null || values.isEmpty()) return Map.of();
        Map<String, Object> copy = new java.util.LinkedHashMap<>();
        values.forEach((key, value) -> {
            Object immutable = value instanceof List<?> list ? List.copyOf(list)
                    : value instanceof Map<?, ?> map ? Map.copyOf(map)
                    : value;
            copy.put(key, immutable);
        });
        return Map.copyOf(copy);
    }
}
