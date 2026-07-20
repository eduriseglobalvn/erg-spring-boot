package com.erg.ergspringboot.platform.web;

import com.erg.ergspringboot.platform.performance.RequestExecutionContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs Hibernate statement count per HTTP request. Counters are request-local,
 * so concurrent requests cannot corrupt each other's diagnostics.
 */
@Component
public class HibernateQueryCountFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HibernateQueryCountFilter.class);
    private static final int WARN_THRESHOLD = 10;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String route = request.getMethod() + " " + request.getRequestURI();
        RequestExecutionContext.Snapshot snapshot;
        try (RequestExecutionContext.Scope ignored = RequestExecutionContext.open(route)) {
            filterChain.doFilter(request, response);
            snapshot = RequestExecutionContext.currentSnapshot();
        }
        long queryCount = snapshot.statementCount();

        if (queryCount > WARN_THRESHOLD) {
            log.warn("Hibernate N+1 suspicion: {} triggered {} statements (threshold: {})",
                    route, queryCount, WARN_THRESHOLD);
        } else if (log.isDebugEnabled()) {
            log.debug("Hibernate: {} triggered {} statements", route, queryCount);
        }
    }
}
