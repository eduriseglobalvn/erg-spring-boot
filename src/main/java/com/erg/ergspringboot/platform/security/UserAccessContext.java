package com.erg.ergspringboot.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public record UserAccessContext(
        String userId,
        String tenantId,
        List<String> managedEducationUnitIds,
        List<String> managedClassIds,
        String currentEducationUnitId,
        String currentClassId,
        @JsonIgnore AccessScopeSet scopeSet
) {

    public UserAccessContext(
            String userId,
            String tenantId,
            List<String> managedEducationUnitIds,
            List<String> managedClassIds,
            String currentEducationUnitId,
            String currentClassId
    ) {
        this(userId, tenantId, managedEducationUnitIds, managedClassIds,
                currentEducationUnitId, currentClassId,
                AccessScopeSet.legacy(managedEducationUnitIds, managedClassIds,
                        currentEducationUnitId, currentClassId));
    }

    public UserAccessContext {
        scopeSet = scopeSet == null
                ? AccessScopeSet.legacy(managedEducationUnitIds, managedClassIds,
                        currentEducationUnitId, currentClassId)
                : scopeSet;
        managedEducationUnitIds = scopeSet.managedIds(AccessScopeSet.EDUCATION_UNIT);
        managedClassIds = scopeSet.managedIds(AccessScopeSet.CLASS);
        currentEducationUnitId = scopeSet.currentId(AccessScopeSet.EDUCATION_UNIT).orElse(null);
        currentClassId = scopeSet.currentId(AccessScopeSet.CLASS).orElse(null);
    }

    public static UserAccessContext from(HttpServletRequest request) {
        ErgPrincipal principal = principal();
        String userId = first(principal == null ? null : principal.userId());
        String tenantId = first(principal == null ? null : principal.tenantId());
        if (principal == null || userId == null || tenantId == null) {
            throw new AuthenticationRequiredException();
        }
        AccessScopeSet principalScopes = AccessScopeAssembler.from(principal);
        List<String> educationUnits = principalScopes.managedIds(AccessScopeSet.EDUCATION_UNIT);
        List<String> classes = principalScopes.managedIds(AccessScopeSet.CLASS);
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

    public String scopeFingerprint() {
        return scopeSet.fingerprint();
    }

    private static ErgPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof ErgPrincipal principal) {
            return principal;
        }
        return null;
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
