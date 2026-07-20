package com.erg.ergspringboot.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Captures the authenticated tenant/user/portal into a request-scoped {@link TenantContext} (spec §1,
 * Phase 0.4). This is the groundwork for PostgreSQL row-level security: later phases push these values
 * into session GUCs so RLS policies can read {@code current_setting('app.tenant_id')}. RLS is not yet
 * enforced — this filter only establishes and clears the per-request identity.
 *
 * <p>Runs after {@link ErgAuthenticationFilter} has populated the {@link SecurityContextHolder}, so the
 * principal is available. When no principal is present (public routes, background paths) the context is
 * left unset and downstream GUC application is skipped.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        ErgPrincipal principal = principal();
        if (principal != null) {
            TenantContext.set(TenantContext.of(
                    principal.tenantId(),
                    principal.userId(),
                    firstPortal(principal.portals())
            ));
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private ErgPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ErgPrincipal principal) {
            return principal;
        }
        return null;
    }

    private String firstPortal(List<String> portals) {
        if (portals == null) {
            return null;
        }
        return portals.stream()
                .filter(portal -> portal != null && !portal.isBlank() && !"*".equals(portal))
                .findFirst()
                .orElse(null);
    }
}
