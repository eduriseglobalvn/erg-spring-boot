package com.erg.ergspringboot.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public record UserAccessContext(
        String userId,
        String tenantId,
        List<String> managedEducationUnitIds,
        List<String> managedClassIds,
        String currentEducationUnitId,
        String currentClassId
) {

    public static UserAccessContext from(HttpServletRequest request) {
        ErgPrincipal principal = principal();
        String userId = first(principal == null ? null : principal.userId(), "system");
        String tenantId = first(principal == null ? null : principal.tenantId(), "erg");
        boolean wildcardPrincipal = principal != null && principal.permissions() != null && principal.permissions().contains("*");
        List<String> educationUnits = wildcardPrincipal ? List.of("*") : csv(attribute(principal, "managedEducationUnitIds"));
        List<String> classes = wildcardPrincipal ? List.of("*") : csv(attribute(principal, "managedClassIds"));
        String requestedEducationUnitId = first(
                request.getHeader("X-Current-EducationUnit-ID"),
                request.getHeader("X-Current-Education-Unit-ID"),
                request.getHeader("X-Current-Center-ID"),
                request.getHeader("X-EducationUnit-ID"),
                request.getHeader("X-Education-Unit-ID"),
                request.getHeader("X-Center-ID")
        );
        String currentEducationUnitId = scopedSelection(requestedEducationUnitId, educationUnits);
        String currentClassId = scopedSelection(first(request.getHeader("X-Current-Class-ID"), request.getHeader("X-Class-ID")), classes);
        return new UserAccessContext(userId, tenantId, educationUnits, classes, currentEducationUnitId, currentClassId);
    }

    public boolean canAccessEducationUnit(String educationUnitId) {
        return educationUnitId == null || educationUnitId.isBlank()
                || managedEducationUnitIds.contains("*") || managedEducationUnitIds.contains(educationUnitId);
    }

    public boolean canAccessClass(String classId) {
        return classId == null || classId.isBlank() || managedClassIds.contains("*") || managedClassIds.contains(classId);
    }

    public boolean hasManagedEducationUnitScope() {
        return !managedEducationUnitIds.isEmpty();
    }

    public boolean hasManagedClassScope() {
        return !managedClassIds.isEmpty();
    }

    private static ErgPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ErgPrincipal principal) {
            return principal;
        }
        return null;
    }

    private static String attribute(ErgPrincipal principal, String key) {
        if (principal == null || principal.attributes() == null) {
            return null;
        }
        Object value = principal.attributes().get(key);
        if (value instanceof List<?> list) {
            return String.join(",", list.stream().map(String::valueOf).toList());
        }
        return value == null ? null : value.toString();
    }

    private static List<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String firstOrNull(List<String> values) {
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private static String scopedSelection(String requested, List<String> managedIds) {
        if (managedIds == null || managedIds.isEmpty()) {
            return null;
        }
        if (requested == null || requested.isBlank()) {
            return firstOrNull(managedIds);
        }
        String normalized = requested.trim();
        if (managedIds.contains("*") || managedIds.contains(normalized)) {
            return normalized;
        }
        return firstOrNull(managedIds);
    }
}
