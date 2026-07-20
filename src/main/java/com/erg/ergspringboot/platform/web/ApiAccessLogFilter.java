package com.erg.ergspringboot.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiAccessLogFilter.class);
    private static final String MASKED_VALUE = "***";
    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern NUMERIC_ID_PATTERN = Pattern.compile("^\\d+$");

    private final boolean maskSensitive;

    public ApiAccessLogFilter(@Value("${erg.logging.mask-sensitive:true}") boolean maskSensitive) {
        this.maskSensitive = maskSensitive;
    }

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
            String maskedPath = maskUriPath(request.getRequestURI());
            String path = queryString == null || queryString.isBlank()
                    ? maskedPath
                    : maskedPath + "?" + maskQueryString(queryString);
            String requestId = toText(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
            String tenantId = firstPresent(principalTenantId(), request.getHeader("X-Tenant-ID"), "erg");
            String userId = firstPresent(principalUserId(), request.getHeader("X-User-ID"), "anonymous");

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

    private String maskUriPath(String uri) {
        if (!maskSensitive || uri == null) {
            return uri;
        }
        String[] segments = uri.split("/");
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isBlank()) {
                continue;
            }
            if (UUID_PATTERN.matcher(segment).matches()) {
                segments[i] = ":" + MASKED_VALUE;
            } else if (NUMERIC_ID_PATTERN.matcher(segment).matches() && segment.length() >= 6) {
                segments[i] = ":" + MASKED_VALUE;
            } else if (isSensitivePathToken(segment)) {
                segments[i] = ":" + MASKED_VALUE;
            }
        }
        return String.join("/", segments);
    }

    private boolean isSensitivePathToken(String segment) {
        String lower = segment.toLowerCase(Locale.ROOT);
        return lower.contains("token")
                || lower.contains("secret")
                || lower.contains("password")
                || lower.contains("credential")
                || lower.startsWith("sk-")
                || lower.startsWith("pk-");
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

    private String maskQueryString(String queryString) {
        if (!maskSensitive) {
            return queryString;
        }

        String[] pairs = queryString.split("&", -1);
        for (int index = 0; index < pairs.length; index++) {
            pairs[index] = maskQueryPair(pairs[index]);
        }
        return String.join("&", pairs);
    }

    private String maskQueryPair(String pair) {
        int separator = pair.indexOf('=');
        String rawName = separator < 0 ? pair : pair.substring(0, separator);
        String name = decode(rawName).toLowerCase(Locale.ROOT);
        if (!isSensitiveQueryParam(name)) {
            return pair;
        }

        String encodedMask = URLEncoder.encode(MASKED_VALUE, StandardCharsets.UTF_8);
        return separator < 0 ? rawName + "=" + encodedMask : rawName + "=" + encodedMask;
    }

    private boolean isSensitiveQueryParam(String name) {
        return name.contains("token")
                || name.contains("secret")
                || name.contains("password")
                || name.contains("credential")
                || name.contains("authorization")
                || name.contains("apikey")
                || name.contains("api_key")
                || name.equals("key")
                || name.endsWith("_key")
                || name.endsWith("-key");
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }
}
