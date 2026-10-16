package com.erg.ergspringboot.platform.graphql;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.AuthorizationCapabilityPolicy;
import com.erg.ergspringboot.platform.security.AccessScopeAssembler;
import com.erg.ergspringboot.platform.security.AccessScopeSet;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class GraphQlAccessSupport {

    private final ErgPolicyService policyService;

    public GraphQlAccessSupport(ErgPolicyService policyService) {
        this.policyService = policyService;
    }

    public ErgPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ErgPrincipal principal) {
            return principal;
        }
        return null;
    }

    public String verifiedTenantId(ErgPrincipal principal, String requestedTenantId) {
        String principalTenantId = AuthenticatedTenant.require(principal == null ? null : principal.tenantId());
        String requested = blankToNull(requestedTenantId);
        if (requested != null && !principalTenantId.equals(requested)) {
            throw new ForbiddenException("GraphQL tenant scope does not match the authenticated principal.");
        }
        return principalTenantId;
    }

    public void assertPermission(ErgPrincipal principal, String... allowedPermissions) {
        if (principal == null) {
            throw new ForbiddenException("GraphQL field requires an authenticated principal.");
        }
        if (isAllowed(principal, allowedPermissions)) {
            return;
        }
        throw new ForbiddenException("GraphQL field permission is missing.");
    }

    public boolean isAllowed(ErgPrincipal principal, String... allowedPermissions) {
        if (principal == null) {
            return false;
        }
        return policyService.isAllowedAny(
                principal,
                null,
                principal.tenantId(),
                Arrays.asList(allowedPermissions)
        );
    }

    public void assertEducationUnitScope(ErgPrincipal principal, String educationUnitId) {
        if (isWildcardPrincipal(principal)) {
            return;
        }
        String requested = blankToNull(educationUnitId);
        if (requested == null) {
            return;
        }
        List<String> managedEducationUnitIds = AccessScopeAssembler.from(principal)
                .managedIds(AccessScopeSet.EDUCATION_UNIT);
        if (managedEducationUnitIds.contains("*") || managedEducationUnitIds.contains(requested)) {
            return;
        }
        throw new ForbiddenException("GraphQL education unit scope is not allowed for the authenticated principal.");
    }

    public void assertClassScope(ErgPrincipal principal, String classId, String schoolId) {
        if (isWildcardPrincipal(principal)) {
            return;
        }
        String requested = blankToNull(classId);
        if (requested == null) {
            throw new ForbiddenException("GraphQL class scope requires a class id.");
        }
        AccessScopeSet scopes = AccessScopeAssembler.from(principal);
        List<String> managedClassIds = scopes.managedIds(AccessScopeSet.CLASS);
        if (managedClassIds.contains("*") || managedClassIds.contains(requested)) {
            return;
        }
        List<String> managedEducationUnitIds = scopes.managedIds(AccessScopeSet.EDUCATION_UNIT);
        String requestedSchoolId = blankToNull(schoolId);
        if (requestedSchoolId != null
                && (managedEducationUnitIds.contains("*") || managedEducationUnitIds.contains(requestedSchoolId))) {
            return;
        }
        throw new ForbiddenException("GraphQL class scope is not allowed for the authenticated principal.");
    }

    public void assertClassOrEducationUnitResponseScope(ErgPrincipal principal, String classId, String educationUnitId) {
        if (isWildcardPrincipal(principal)) {
            return;
        }
        String requestedClassId = blankToNull(classId);
        List<String> managedClassIds = AccessScopeAssembler.from(principal).managedIds(AccessScopeSet.CLASS);
        if (requestedClassId != null
                && (managedClassIds.contains("*") || managedClassIds.contains(requestedClassId))) {
            return;
        }
        assertEducationUnitScope(principal, educationUnitId);
    }

    public String academicYear(ErgPrincipal principal, String requestedAcademicYear) {
        String requested = blankToNull(requestedAcademicYear);
        if (requested != null) {
            return requested;
        }
        return firstAttribute(principal, "currentAcademicYear", "academicYear", "schoolYear");
    }

    public String attribute(ErgPrincipal principal, String key) {
        if (principal == null || principal.attributes() == null) {
            return null;
        }
        Object value = principal.attributes().get(key);
        if (value instanceof List<?> list) {
            return String.join(",", list.stream().map(String::valueOf).toList());
        }
        return value == null ? null : value.toString();
    }

    public List<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    public String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public boolean isWildcardPrincipal(ErgPrincipal principal) {
        return new AuthorizationCapabilityPolicy().isPlatformWide(principal);
    }

    private String firstAttribute(ErgPrincipal principal, String... keys) {
        for (String key : keys) {
            String value = blankToNull(attribute(principal, key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
