package com.erg.ergspringboot.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ErgAuthenticationFilter extends OncePerRequestFilter {

    private final ErgPrincipalService principalService;

    public ErgAuthenticationFilter(ErgPrincipalService principalService) {
        this.principalService = principalService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null && hasAuthenticationInput(request)) {
            java.util.Optional<ErgPrincipal> principal = principalFrom(request);
            if (principal.isPresent() && !canAccessRequestedPortal(request, principal.get())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Portal session does not match requested API.");
                return;
            }
            principal.ifPresent(this::authenticate);
        }
        filterChain.doFilter(request, response);
    }

    private boolean hasAuthenticationInput(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return (authorization != null && authorization.startsWith("Bearer "))
                || request.getHeader("X-User-ID") != null
                || request.getHeader("X-Roles") != null
                || request.getHeader("X-Permissions") != null;
    }

    private java.util.Optional<ErgPrincipal> principalFrom(HttpServletRequest request) {
        String bearerToken = bearerToken(request);
        if (bearerToken != null) {
            return principalService.fromBearerToken(bearerToken, request.getHeader("X-Tenant-ID"));
        }
        String userId = firstPresent(request.getHeader("X-User-ID"), "token-user");
        String sessionId = firstPresent(request.getHeader("X-Session-ID"), "token-session");
        String tenantId = firstPresent(request.getHeader("X-Tenant-ID"), "erg");
        List<String> roles = csv(request.getHeader("X-Roles"));
        List<String> permissions = csv(request.getHeader("X-Permissions"));
        List<String> deniedPermissions = csv(request.getHeader("X-Denied-Permissions"));
        List<String> portals = csvOrDefault(request.getHeader("X-Portals"), List.of("*"));
        return java.util.Optional.of(new ErgPrincipal(
                userId,
                sessionId,
                tenantId,
                roles,
                permissions,
                deniedPermissions,
                portals,
                firstPresent(request.getHeader("X-Account-Type"), "erg"),
                firstPresent(request.getHeader("X-Access-Level"), ""),
                Map.of(
                        "managedEducationUnitIds", csv(firstPresent(
                                request.getHeader("X-Managed-EducationUnit-IDs"),
                                firstPresent(request.getHeader("X-Managed-Education-Unit-IDs"), request.getHeader("X-Managed-Center-IDs"))
                        )),
                        "managedClassIds", csv(request.getHeader("X-Managed-Class-IDs")),
                        "currentEducationUnitId", firstPresent(
                                request.getHeader("X-Current-EducationUnit-ID"),
                                firstPresent(
                                        request.getHeader("X-Current-Education-Unit-ID"),
                                        firstPresent(
                                                request.getHeader("X-Current-Center-ID"),
                                                firstPresent(
                                                        request.getHeader("X-EducationUnit-ID"),
                                                        firstPresent(
                                                                request.getHeader("X-Education-Unit-ID"),
                                                                firstPresent(request.getHeader("X-Center-ID"), "")
                                                        )
                                                )
                                        )
                                )
                        ),
                        "currentClassId", firstPresent(request.getHeader("X-Current-Class-ID"), firstPresent(request.getHeader("X-Class-ID"), ""))
                )
        ));
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
        String requiredPortal = portalFromPath(request.getRequestURI());
        if (requiredPortal == null) {
            return true;
        }
        return principal.portals().contains("*") || principal.portals().contains(requiredPortal);
    }

    private String portalFromPath(String path) {
        String normalized = path == null ? "" : path.toLowerCase();
        if (normalized.startsWith("/api/v1/admin") || normalized.startsWith("/api/admin")) return "admin";
        if (normalized.startsWith("/api/v1/users") || normalized.startsWith("/api/users")) {
            if (normalized.startsWith("/api/v1/users/me")) return null;
            return "admin";
        }
        if (normalized.startsWith("/api/lms/access-management") || normalized.startsWith("/api/lms/auth/accounts")) return "admin";
        if (normalized.startsWith("/api/v1/hoclieu") || normalized.startsWith("/api/hoclieu")) return "hoclieu";
        if (normalized.startsWith("/api/v1/elearning") || normalized.startsWith("/api/elearning")) return "elearning";
        if (normalized.startsWith("/api/v1/lms") || normalized.startsWith("/api/lms")) return "lms";
        if (normalized.startsWith("/api/v1/sessions") || normalized.startsWith("/api/v1/users/me")) return null;
        return null;
    }

    private String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring("Bearer ".length()).trim();
        return token.isBlank() ? null : token;
    }

    private List<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private List<String> csvOrDefault(String value, List<String> fallback) {
        List<String> parsed = csv(value);
        return parsed.isEmpty() ? fallback : parsed;
    }

    private String firstPresent(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
