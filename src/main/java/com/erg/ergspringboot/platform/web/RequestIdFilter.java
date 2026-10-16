package com.erg.ergspringboot.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = RequestCorrelation.REQUEST_ID_HEADER;
    public static final String REQUEST_ID_ATTRIBUTE = RequestCorrelation.REQUEST_ID_ATTRIBUTE;
    public static final String TRACE_ID_HEADER = RequestCorrelation.TRACE_ID_HEADER;
    public static final String REQUEST_ID_MDC_KEY = RequestCorrelation.REQUEST_ID_MDC_KEY;
    public static final String TRACE_ID_MDC_KEY = RequestCorrelation.TRACE_ID_MDC_KEY;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = RequestCorrelation.getOrCreate(request);
        String traceId = request.getHeader(TRACE_ID_HEADER);

        response.setHeader(REQUEST_ID_HEADER, requestId);

        // Populate MDC so downstream service/repo logs carry the correlation id.
        // Must clear in finally to avoid leaking onto pooled threads.
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TRACE_ID_MDC_KEY, traceId);
        }
        try {
            filterChain.doFilter(RequestCorrelation.exposeAsHeader(request, requestId), response);
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY);
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}
