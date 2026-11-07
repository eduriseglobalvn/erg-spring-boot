package com.erg.ergspringboot.platform.graphql;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GraphQlGuardFilter extends OncePerRequestFilter {

    private static final String ALL_OPERATIONS = "operation:all";

    private final ObjectMapper objectMapper;
    private final ErgGraphQlProperties properties;
    private final GraphQlRateLimiter rateLimiter;
    private final String graphQlPath;

    public GraphQlGuardFilter(
            ObjectMapper objectMapper,
            ErgGraphQlProperties properties,
            GraphQlRateLimiter rateLimiter,
            @Value("${spring.graphql.http.path:/graphql}") String graphQlPath
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.graphQlPath = normalizePath(graphQlPath);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // A browser preflight carries no query; the CORS layer answers it, and a 405 here blocks every cross-origin call.
        return !graphQlPath.equals(request.getRequestURI()) || CorsUtils.isPreFlightRequest(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            writeError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                    "GraphQL only accepts POST requests.");
            return;
        }

        if (request.getContentLengthLong() > properties.getMaxRequestBodyBytes()) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                    "GraphQL request body is too large.");
            return;
        }
        byte[] body = readRequestBody(request.getInputStream(), properties.getMaxRequestBodyBytes());
        if (body == null) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                    "GraphQL request body is too large.");
            return;
        }
        JsonNode payload = parsePayload(request, body);
        if (payload == null) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST",
                    "GraphQL request body must be JSON.");
            return;
        }

        String query = text(payload, "query");
        if (query == null || query.isBlank()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST",
                    "GraphQL query is required.");
            return;
        }
        if (query.length() > properties.getMaxQueryLength()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST",
                    "GraphQL query is too large.");
            return;
        }
        if (GraphQlRequestInspector.maxDepth(query) > properties.getMaxDepth()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST",
                    "GraphQL query depth exceeds allowed limit.");
            return;
        }
        if (GraphQlRequestInspector.aliasCount(query) > properties.getMaxAliases()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST",
                    "GraphQL query alias count exceeds allowed limit.");
            return;
        }
        String operationName = text(payload, "operationName");
        if (properties.isRequireOperationName()) {
            if (operationName == null || operationName.isBlank()) {
                writeError(response, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST",
                        "GraphQL operationName is required.");
                return;
            }
        }
        GraphQlRateLimiter.Decision rateLimit = rateLimiter.allow(rateLimitKey(request));
        if (!rateLimit.allowed()) {
            response.setHeader("Retry-After", String.valueOf(rateLimit.retryAfterSeconds()));
            writeError(response, 429, "RATE_LIMITED", "GraphQL rate limit exceeded.");
            return;
        }
        if (!properties.isIntrospectionEnabled() && GraphQlRequestInspector.containsIntrospection(query)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                    "GraphQL introspection is disabled.");
            return;
        }
        if (properties.isRejectSensitiveVariables() && GraphQlRequestInspector.containsSensitiveVariable(payload.get("variables"))) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST",
                    "GraphQL variables contain sensitive fields.");
            return;
        }
        if (GraphQlRequestInspector.containsOversizedVariableArray(payload.get("variables"), properties.getMaxVariableArrayItems())) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST",
                    "GraphQL variable arrays exceed the allowed cardinality.");
            return;
        }

        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    /**
     * One bucket per caller. operationName, tenant headers and Authorization values are chosen
     * by the client, so none of them may split a caller's quota into fresh buckets.
     */
    private GraphQlRateLimitKey rateLimitKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof ErgPrincipal principal) {
            return new GraphQlRateLimitKey(
                    stablePart(principal.tenantId(), "tenant:authenticated"),
                    stablePart(principal.userId(), "user:authenticated"),
                    ALL_OPERATIONS
            );
        }

        return new GraphQlRateLimitKey(
                "tenant:anonymous",
                "ip:" + stablePart(request.getRemoteAddr(), "unknown"),
                ALL_OPERATIONS
        );
    }

    private String stablePart(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/graphql";
        }
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "errors", List.of(Map.of(
                        "message", message,
                        "extensions", Map.of("code", code)
                ))
        ));
    }

    private JsonNode parsePayload(HttpServletRequest request, byte[] body) throws IOException {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith(MediaType.APPLICATION_JSON_VALUE)) {
            return null;
        }
        try {
            JsonNode payload = objectMapper.readTree(body);
            return payload != null && payload.isObject() ? payload : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private byte[] readRequestBody(InputStream input, int maxBytes) throws IOException {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxRequestBodyBytes must be positive");
        }
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8_192))) {
            byte[] buffer = new byte[8_192];
            int remaining = maxBytes;
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read > remaining) {
                    return null;
                }
                output.write(buffer, 0, read);
                remaining -= read;
            }
            return output.toByteArray();
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Async reads are not supported for cached GraphQL body.");
                }

                @Override
                public int read() {
                    return input.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
