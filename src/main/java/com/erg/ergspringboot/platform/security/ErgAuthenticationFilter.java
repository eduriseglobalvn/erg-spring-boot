package com.erg.ergspringboot.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ErgAuthenticationFilter extends OncePerRequestFilter {

    static final String AUTH_FAILURE_CODE_ATTRIBUTE = "erg.auth.failureCode";

    private final ErgPrincipalService principalService;
    private final ErgSecurityProperties securityProperties;
    private final boolean bffEnabled;

    /** Compatibility constructor for stateless bearer mode and focused tests. */
    public ErgAuthenticationFilter(ErgPrincipalService principalService, ErgSecurityProperties securityProperties) {
        this(principalService, securityProperties, false);
    }

    @Autowired
    public ErgAuthenticationFilter(
            ErgPrincipalService principalService,
            ErgSecurityProperties securityProperties,
            ErgAuthProperties authProperties) {
        this(principalService, securityProperties, authProperties.isBffEnabled());
    }

    /** Compatibility constructor for focused unit tests and old embedders. */
    public ErgAuthenticationFilter(
            ErgPrincipalService principalService,
            ErgSecurityProperties securityProperties,
            boolean bffEnabled) {
        this.principalService = principalService;
        this.securityProperties = securityProperties;
        this.bffEnabled = bffEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (bffEnabled && bearerToken(request) != null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "BFF_COOKIE_AUTH_REQUIRED");
            return;
        }
        if (SecurityContextHolder.getContext().getAuthentication() == null && hasAuthenticationInput(request)) {
            Optional<ErgPrincipal> principal = principalFrom(request);
            if (principal.isEmpty()) {
                markBearerAuthenticationFailure(request);
            }
            if (principal.isPresent() && !canAccessRequestedPortal(request, principal.get())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Portal session does not match requested API.");
                return;
            }
            if (principal.isPresent() && isLifecycleRestricted(principal.get()) && !isOnboardingRequest(request)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "ACCOUNT_LIFECYCLE_RESTRICTED");
                return;
            }
            principal.ifPresent(this::authenticate);
        }
        filterChain.doFilter(request, response);
    }

    private void markBearerAuthenticationFailure(HttpServletRequest request) {
        if (request.getAttribute(AUTH_FAILURE_CODE_ATTRIBUTE) != null) {
            return;
        }
        String bearerToken = bearerToken(request);
        if (bearerToken == null) {
            return;
        }
        String requestedTenantId = SecurityUtils.trimToNull(request.getHeader("X-Tenant-ID"));
        Supplier<Optional<String>> lookup = requestedTenantId == null
                ? () -> principalService.authenticationFailureCode(bearerToken)
                : () -> withBearerLookupContext(request, () -> principalService.authenticationFailureCode(
                        bearerToken, requestedTenantId));
        lookup.get()
                .ifPresent(code -> request.setAttribute(AUTH_FAILURE_CODE_ATTRIBUTE, code));
    }

    private boolean hasAuthenticationInput(HttpServletRequest request) {
        if (bearerToken(request) != null) {
            return true;
        }
        return securityProperties.isTrustedHeaderAuthEnabled()
                && (request.getHeader("X-User-ID") != null
                || request.getHeader("X-Roles") != null
                || request.getHeader("X-Permissions") != null);
    }

    private Optional<ErgPrincipal> principalFrom(HttpServletRequest request) {
        String bearerToken = bearerToken(request);
        if (bearerToken != null) {
            String requestedTenantId = SecurityUtils.trimToNull(request.getHeader("X-Tenant-ID"));
            if (requestedTenantId == null) {
                Optional<String> resolvedTenantId = principalService.resolveTenantId(bearerToken);
                if (resolvedTenantId.isEmpty()) {
                    request.setAttribute(AUTH_FAILURE_CODE_ATTRIBUTE, "AUTHENTICATION_REQUIRED");
                    return Optional.empty();
                }
                return withBearerLookupContext(request, resolvedTenantId.get(), () -> principalService.fromBearerToken(
                        bearerToken, resolvedTenantId.get()));
            }
            return withBearerLookupContext(request, () -> principalService.fromBearerToken(
                    bearerToken, requestedTenantId));
        }
        if (!securityProperties.isTrustedHeaderAuthEnabled()) {
            return Optional.empty();
        }
        return Optional.of(headerPrincipal(request));
    }

    /**
     * Installs only the tenant lookup hint before the principal service opens its RLS-bound transaction.
     * The bearer token plus tenant ID still identifies the session; this context does not authenticate
     * the request and is removed before the verified principal is installed for downstream processing.
     */
    private <T> T withBearerLookupContext(HttpServletRequest request, Supplier<T> lookup) {
        return withBearerLookupContext(request, SecurityUtils.trimToNull(request.getHeader("X-Tenant-ID")), lookup);
    }

    private <T> T withBearerLookupContext(
            HttpServletRequest request,
            String resolvedTenantId,
            Supplier<T> lookup) {
        TenantContext previous = TenantContext.current();
        TenantContext.set(TenantContext.of(
                resolvedTenantId,
                null,
                SecurityUtils.portalFromPath(request.getRequestURI())
        ));
        try {
            return lookup.get();
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previous);
            }
        }
    }

    private ErgPrincipal headerPrincipal(HttpServletRequest request) {
        String userId = SecurityUtils.firstPresent(request.getHeader("X-User-ID"), "token-user");
        String sessionId = SecurityUtils.firstPresent(request.getHeader("X-Session-ID"), "token-session");
        String tenantId = AuthenticatedTenant.require(request.getHeader("X-Tenant-ID"));
        List<String> roles = SecurityUtils.csv(request.getHeader("X-Roles"));
        List<String> permissions = SecurityUtils.csv(request.getHeader("X-Permissions"));
        List<String> deniedPermissions = SecurityUtils.csv(request.getHeader("X-Denied-Permissions"));
        // Portal headers are compatibility metadata only; an absent header must
        // not become a wildcard grant. Workspace authority is derived later
        // from verified roles/permissions.
        List<String> portals = SecurityUtils.csv(request.getHeader("X-Portals"));
        String managedEducationUnitIds = SecurityUtils.firstNonBlank(
                request.getHeader("X-Managed-EducationUnit-IDs"),
                request.getHeader("X-Managed-Education-Unit-IDs"),
                request.getHeader("X-Managed-Center-IDs")
        );
        String currentEducationUnitId = SecurityUtils.firstNonBlank(
                request.getHeader("X-Current-EducationUnit-ID"),
                request.getHeader("X-Current-Education-Unit-ID"),
                request.getHeader("X-Current-Center-ID"),
                request.getHeader("X-EducationUnit-ID"),
                request.getHeader("X-Education-Unit-ID"),
                request.getHeader("X-Center-ID")
        );
        String currentClassId = SecurityUtils.firstNonBlank(
                request.getHeader("X-Current-Class-ID"),
                request.getHeader("X-Class-ID")
        );
        return ErgPrincipal.authenticated(
                userId,
                sessionId,
                tenantId,
                roles,
                permissions,
                deniedPermissions,
                portals,
                SecurityUtils.firstPresent(request.getHeader("X-Account-Type"), "erg"),
                SecurityUtils.firstPresent(request.getHeader("X-Access-Level"), ""),
                AccessScopeAssembler.toLegacyAttributes(AccessScopeSet.legacy(
                        SecurityUtils.csv(managedEducationUnitIds),
                        SecurityUtils.csv(request.getHeader("X-Managed-Class-IDs")),
                        currentEducationUnitId,
                        currentClassId))
        );
    }

    private void authenticate(ErgPrincipal principal) {
        List<SimpleGrantedAuthority> authorities = java.util.stream.Stream.concat(
                        principal.roles().stream().map(role -> new SimpleGrantedAuthority("role:" + role)),
                        principal.permissions().stream().map(permission -> new SimpleGrantedAuthority("perm:" + permission))
                )
                .toList();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private boolean canAccessRequestedPortal(HttpServletRequest request, ErgPrincipal principal) {
        String requiredPortal = SecurityUtils.portalFromPath(request.getRequestURI());
        if (requiredPortal == null) {
            return true;
        }
        return new AuthorizationCapabilityPolicy().hasWorkspace(principal, requiredPortal);
    }

    private boolean isLifecycleRestricted(ErgPrincipal principal) {
        return Boolean.TRUE.equals(principal.attributes().get("lifecycleRestricted"));
    }

    private boolean isOnboardingRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/v1/users/me")
                || path.startsWith("/api/v1/users/me/")
                || path.startsWith("/api/v1/sessions/")
                || path.equals("/api/v1/auth/logout")
                || path.equals("/api/v1/auth/refresh");
    }

    private String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring("Bearer ".length()).trim();
        return token.isBlank() ? null : token;
    }

    private List<String> csvOrDefault(String value, List<String> fallback) {
        List<String> parsed = SecurityUtils.csv(value);
        return parsed.isEmpty() ? fallback : parsed;
    }
}
