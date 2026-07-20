package com.erg.ergspringboot.platform.security;

import com.erg.ergspringboot.exception.UnauthorizedException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private static final String DEFAULT_TENANT = "erg";

    private SecurityUtils() {}

    public static ErgPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ErgPrincipal p) {
            return p;
        }
        return null;
    }

    public static ErgPrincipal requirePrincipal() {
        ErgPrincipal p = principal();
        if (p == null) {
            throw new UnauthorizedException("missing authenticated user");
        }
        return p;
    }

    public static List<String> csv(String value) {
        if (isBlank(value)) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    public static String firstPresent(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    public static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!isBlank(v)) return v.trim();
        }
        return null;
    }

    public static String portalFromPath(String path) {
        String normalized = path == null ? "" : path.toLowerCase();
        if (normalized.startsWith("/api/v1/admin") || normalized.startsWith("/api/admin")) return "admin";
        if (normalized.startsWith("/api/v1/users") || normalized.startsWith("/api/users")) {
            if (normalized.startsWith("/api/v1/users/me")) return null;
            return "admin";
        }
        if (normalized.startsWith("/api/lms/access-management") || normalized.startsWith("/api/lms/auth/accounts")) return "admin";
        if (normalized.startsWith("/api/content") || normalized.startsWith("/api/curriculum")) return "lcms";
        if (normalized.startsWith("/api/v1/elearning") || normalized.startsWith("/api/elearning")) return "elearning";
        if (normalized.startsWith("/api/v1/lms") || normalized.startsWith("/api/lms")) return "lms";
        if (normalized.startsWith("/api/v1/sessions") || normalized.startsWith("/api/v1/users/me")) return null;
        return null;
    }

    public static String normalizeTenant(String tenantId) {
        return isBlank(tenantId) ? DEFAULT_TENANT : tenantId.trim();
    }

    public static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
