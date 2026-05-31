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
        String userId = first(request.getHeader("X-User-ID"), principal == null ? null : principal.userId(), "system");
        String tenantId = first(request.getHeader("X-Tenant-ID"), principal == null ? null : principal.tenantId(), "erg");
        List<String> educationUnits = csv(first(
                request.getHeader("X-Managed-EducationUnit-IDs"),
                request.getHeader("X-Managed-Education-Unit-IDs"),
                request.getHeader("X-Managed-Center-IDs"),
                attribute(principal, "managedEducationUnitIds"),
                request.getHeader("X-EducationUnit-ID"),
                request.getHeader("X-Education-Unit-ID"),
                request.getHeader("X-Center-ID")
        ));
        List<String> classes = csv(first(
                request.getHeader("X-Managed-Class-IDs"),
                attribute(principal, "managedClassIds"),
                request.getHeader("X-Class-ID")
        ));
        String currentEducationUnitId = first(
                request.getHeader("X-Current-EducationUnit-ID"),
                request.getHeader("X-Current-Education-Unit-ID"),
                request.getHeader("X-Current-Center-ID"),
                request.getHeader("X-EducationUnit-ID"),
                request.getHeader("X-Education-Unit-ID"),
                request.getHeader("X-Center-ID"),
                firstOrNull(educationUnits)
        );
        String currentClassId = first(request.getHeader("X-Current-Class-ID"), request.getHeader("X-Class-ID"), firstOrNull(classes));
        return new UserAccessContext(userId, tenantId, educationUnits, classes, currentEducationUnitId, currentClassId);
    }

    public boolean canAccessEducationUnit(String educationUnitId) {
        return educationUnitId == null || educationUnitId.isBlank() || managedEducationUnitIds.isEmpty()
                || managedEducationUnitIds.contains("*") || managedEducationUnitIds.contains(educationUnitId);
    }

    public boolean canAccessClass(String classId) {
        return classId == null || classId.isBlank() || managedClassIds.isEmpty() || managedClassIds.contains("*") || managedClassIds.contains(classId);
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
}
