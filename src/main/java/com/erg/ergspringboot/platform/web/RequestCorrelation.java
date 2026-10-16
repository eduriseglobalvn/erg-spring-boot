package com.erg.ergspringboot.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

/** Single source of truth for HTTP/GraphQL request correlation. */
public final class RequestCorrelation {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String REQUEST_ID_ATTRIBUTE = "request_id";
    public static final String LEGACY_REQUEST_ID_ATTRIBUTE = "requestId";
    public static final String TRACE_ID_HEADER = "X-Trace-ID";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private RequestCorrelation() {
    }

    public static String current(HttpServletRequest request) {
        String requestId = text(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        if (requestId == null) {
            requestId = text(request.getAttribute(LEGACY_REQUEST_ID_ATTRIBUTE));
        }
        return requestId == null ? text(request.getHeader(REQUEST_ID_HEADER)) : requestId;
    }

    public static String getOrCreate(HttpServletRequest request) {
        String requestId = current(request);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        bind(request, requestId);
        return requestId;
    }

    public static void bind(HttpServletRequest request, String requestId) {
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        // Temporary compatibility for controllers written before the canonical attribute was introduced.
        request.setAttribute(LEGACY_REQUEST_ID_ATTRIBUTE, requestId);
    }

    public static HttpServletRequest exposeAsHeader(HttpServletRequest request, String requestId) {
        return new RequestIdHeaderRequest(request, requestId);
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static final class RequestIdHeaderRequest extends HttpServletRequestWrapper {
        private final String requestId;

        private RequestIdHeaderRequest(HttpServletRequest request, String requestId) {
            super(request);
            this.requestId = requestId;
        }

        @Override
        public String getHeader(String name) {
            return REQUEST_ID_HEADER.equalsIgnoreCase(name) ? requestId : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return REQUEST_ID_HEADER.equalsIgnoreCase(name)
                    ? Collections.enumeration(List.of(requestId))
                    : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>(Collections.list(super.getHeaderNames()));
            if (names.stream().noneMatch(REQUEST_ID_HEADER::equalsIgnoreCase)) {
                names.add(REQUEST_ID_HEADER);
            }
            return Collections.enumeration(names);
        }
    }
}
