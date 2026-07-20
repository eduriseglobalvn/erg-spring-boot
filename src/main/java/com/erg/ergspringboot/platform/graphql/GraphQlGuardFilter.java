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
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GraphQlGuardFilter extends OncePerRequestFilter {

    private static final Set<String> SENSITIVE_VARIABLE_NAMES = Set.of(
            "password", "passwd", "secret", "token", "access_token", "refresh_token",
            "apikey", "api_key", "credential", "credentials", "authorization"
    );

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
        return !graphQlPath.equals(request.getRequestURI());
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
        if (maxDepth(query) > properties.getMaxDepth()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST",
                    "GraphQL query depth exceeds allowed limit.");
            return;
        }
        if (aliasCount(query) > properties.getMaxAliases()) {
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
        GraphQlRateLimiter.Decision rateLimit = rateLimiter.allow(rateLimitKey(request, operationName));
        if (!rateLimit.allowed()) {
            response.setHeader("Retry-After", String.valueOf(rateLimit.retryAfterSeconds()));
            writeError(response, 429, "RATE_LIMITED", "GraphQL rate limit exceeded.");
            return;
        }
        if (!properties.isIntrospectionEnabled() && containsIntrospection(query)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                    "GraphQL introspection is disabled.");
            return;
        }
        if (properties.isRejectSensitiveVariables() && containsSensitiveVariable(payload.get("variables"))) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST",
                    "GraphQL variables contain sensitive fields.");
            return;
        }
        if (containsOversizedVariableArray(payload.get("variables"), properties.getMaxVariableArrayItems())) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST",
                    "GraphQL variable arrays exceed the allowed cardinality.");
            return;
        }

        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private GraphQlRateLimitKey rateLimitKey(HttpServletRequest request, String operationName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof ErgPrincipal principal) {
            return new GraphQlRateLimitKey(
                    stablePart(principal.tenantId(), "tenant:authenticated"),
                    stablePart(principal.userId(), "user:authenticated"),
                    stablePart(operationName, "operation:anonymous")
            );
        }

        return new GraphQlRateLimitKey(
                stablePart(request.getHeader("X-Tenant-ID"), "tenant:unknown"),
                anonymousCallerId(request),
                stablePart(operationName, "operation:anonymous")
        );
    }

    private String anonymousCallerId(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && !authorization.isBlank()) {
            return "auth:" + Integer.toUnsignedString(authorization.hashCode(), 36);
        }
        return "ip:" + stablePart(request.getRemoteAddr(), "unknown");
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

    private boolean containsIntrospection(String query) {
        return query.contains("__schema") || query.contains("__type");
    }

    private int maxDepth(String query) {
        int depth = 0;
        int max = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (inString) {
                escaped = c == '\\' && !escaped;
                if (c == '"' && !escaped) {
                    inString = false;
                }
                if (c != '\\') {
                    escaped = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
                max = Math.max(max, depth);
            } else if (c == '}') {
                depth = Math.max(0, depth - 1);
            }
        }
        return max;
    }

    private int aliasCount(String query) {
        int aliases = 0;
        int parentheses = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (inString) {
                escaped = c == '\\' && !escaped;
                if (c == '"' && !escaped) {
                    inString = false;
                }
                if (c != '\\') {
                    escaped = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '(') {
                parentheses++;
            } else if (c == ')') {
                parentheses = Math.max(0, parentheses - 1);
            } else if (c == ':' && parentheses == 0 && looksLikeAlias(query, i)) {
                aliases++;
            }
        }
        return aliases;
    }

    private boolean looksLikeAlias(String query, int colonIndex) {
        int before = colonIndex - 1;
        while (before >= 0 && Character.isWhitespace(query.charAt(before))) {
            before--;
        }
        if (before < 0 || !isGraphQlNamePart(query.charAt(before))) {
            return false;
        }
        while (before >= 0 && isGraphQlNamePart(query.charAt(before))) {
            before--;
        }
        int after = colonIndex + 1;
        while (after < query.length() && Character.isWhitespace(query.charAt(after))) {
            after++;
        }
        return after < query.length() && isGraphQlNameStart(query.charAt(after));
    }

    private boolean isGraphQlNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private boolean isGraphQlNamePart(char c) {
        return isGraphQlNameStart(c) || Character.isDigit(c);
    }

    private boolean containsSensitiveVariable(JsonNode variables) {
        if (variables == null || variables.isNull()) {
            return false;
        }
        if (variables.isObject()) {
            for (java.util.Map.Entry<String, JsonNode> entry : variables.properties()) {
                if (isSensitiveName(entry.getKey()) || containsSensitiveVariable(entry.getValue())) {
                    return true;
                }
            }
        } else if (variables.isArray()) {
            for (JsonNode item : variables) {
                if (containsSensitiveVariable(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsOversizedVariableArray(JsonNode node, int maxItems) {
        if (node == null || node.isNull()) return false;
        int safeMax = Math.max(1, maxItems);
        if (node.isArray()) {
            if (node.size() > safeMax) return true;
            for (JsonNode child : node) {
                if (containsOversizedVariableArray(child, safeMax)) return true;
            }
        } else if (node.isObject()) {
            for (java.util.Map.Entry<String, JsonNode> entry : node.properties()) {
                if (containsOversizedVariableArray(entry.getValue(), safeMax)) return true;
            }
        }
        return false;
    }

    private boolean isSensitiveName(String name) {
        String normalized = name == null ? "" : name
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace("-", "_")
                .toLowerCase(Locale.ROOT);
        return SENSITIVE_VARIABLE_NAMES.contains(normalized)
                || normalized.endsWith("_password")
                || normalized.endsWith("_secret")
                || normalized.endsWith("_token")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token");
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
