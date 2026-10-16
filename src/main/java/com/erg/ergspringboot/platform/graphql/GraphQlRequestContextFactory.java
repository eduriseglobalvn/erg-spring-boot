package com.erg.ergspringboot.platform.graphql;

import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.AccessScopeAssembler;
import com.erg.ergspringboot.platform.security.AccessScopeSet;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.platform.web.RequestCorrelation;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class GraphQlRequestContextFactory {

    public GraphQlRequestContext from(HttpHeaders headers, String operationName) {
        ErgPrincipal principal = verifiedPrincipal();
        String requestedTenantId = trimToNull(headers.getFirst("X-Tenant-ID"));
        String requestedPortal = normalizePortal(headers.getFirst("X-Portal"));

        assertTenantMatchesPrincipal(requestedTenantId, principal);
        assertPortalAllowed(requestedPortal, principal);

        AccessScopeSet principalScopes = AccessScopeAssembler.from(principal);
        List<String> educationUnits = principalScopes.managedIds(AccessScopeSet.EDUCATION_UNIT);
        List<String> classes = principalScopes.managedIds(AccessScopeSet.CLASS);
        UserAccessContext access = new UserAccessContext(
                principal.userId(),
                principal.tenantId(),
                educationUnits,
                classes,
                first(
                        headers.getFirst("X-Current-EducationUnit-ID"),
                        headers.getFirst("X-Current-Education-Unit-ID"),
                        headers.getFirst("X-Current-Center-ID"),
                        headers.getFirst("X-EducationUnit-ID"),
                        headers.getFirst("X-Education-Unit-ID"),
                        headers.getFirst("X-Center-ID"),
                        principalScopes.currentId(AccessScopeSet.EDUCATION_UNIT).orElse(null),
                        firstOrNull(educationUnits)
                ),
                first(
                        headers.getFirst("X-Current-Class-ID"),
                        headers.getFirst("X-Class-ID"),
                        principalScopes.currentId(AccessScopeSet.CLASS).orElse(null),
                        firstOrNull(classes)
                )
        );

        return new GraphQlRequestContext(
                principal,
                access,
                principal.userId(),
                principal.tenantId(),
                requestedTenantId,
                requestedPortal,
                first(headers.getFirst(RequestCorrelation.REQUEST_ID_HEADER),
                        headers.getFirst(RequestCorrelation.TRACE_ID_HEADER)),
                trimToNull(operationName),
                hasBearer(headers),
                safeList(principal.roles()),
                safeList(principal.permissions()),
                safeList(principal.deniedPermissions()),
                safeList(principal.portals())
        );
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private ErgPrincipal verifiedPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof ErgPrincipal principal) {
            return principal;
        }
        throw new AuthenticationCredentialsNotFoundException("GraphQL requires a verified principal.");
    }

    private void assertTenantMatchesPrincipal(String requestedTenantId, ErgPrincipal principal) {
        if (requestedTenantId != null && !requestedTenantId.equals(principal.tenantId())) {
            throw new AccessDeniedException("GraphQL tenant header does not match authenticated tenant.");
        }
    }

    private void assertPortalAllowed(String requestedPortal, ErgPrincipal principal) {
        List<String> portals = safeList(principal.portals());
        if (requestedPortal == null
                || portals.contains("*")
                || portals.stream().anyMatch(portal -> portal.equalsIgnoreCase(requestedPortal))) {
            return;
        }
        throw new AccessDeniedException("GraphQL portal header is not allowed for authenticated principal.");
    }

    private boolean hasBearer(HttpHeaders headers) {
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length());
    }

    private String normalizePortal(String portal) {
        String value = trimToNull(portal);
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private String first(String... values) {
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String firstOrNull(List<String> values) {
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
