package com.erg.ergspringboot.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiAccessLogFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startedAt;
            String queryString = request.getQueryString();
            String path = queryString == null || queryString.isBlank()
                    ? request.getRequestURI()
                    : request.getRequestURI() + "?" + queryString;
            String requestId = toText(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
            String tenantId = firstPresent(request.getHeader("X-Tenant-ID"), principalTenantId(), "erg");
            String userId = firstPresent(request.getHeader("X-User-ID"), principalUserId(), "anonymous");

            log.info(
                    "api {} {} -> {} ({} ms) [requestId={}, tenantId={}, userId={}]",
                    request.getMethod(),
                    path,
                    response.getStatus(),
                    durationMs,
                    requestId,
                    tenantId,
                    userId
            );
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/v3/api-docs")
                || path.startsWith("/scalar")
                || path.startsWith("/favicon")
                || path.startsWith("/error");
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String principalUserId() {
        ErgPrincipal principal = principal();
        return principal == null ? null : principal.userId();
    }

    private String principalTenantId() {
        ErgPrincipal principal = principal();
        return principal == null ? null : principal.tenantId();
    }

    private ErgPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ErgPrincipal principal) {
            return principal;
        }
        return null;
    }

    private String toText(Object value) {
        return value == null ? "-" : value.toString();
    }
}
