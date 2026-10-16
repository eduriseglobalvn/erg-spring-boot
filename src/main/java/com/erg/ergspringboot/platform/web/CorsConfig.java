package com.erg.ergspringboot.platform.web;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final String[] REQUIRED_ALLOWED_ORIGINS = {
            "http://localhost:3000",
            "http://localhost:3001",
            "http://localhost:3003",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:3001",
            "http://127.0.0.1:3003",
            "http://crm.erg.edu.vn:3001",
            "http://lcms.erg.edu.vn:3001",
            "http://lms.erg.edu.vn:3001",
            "http://elearning.erg.edu.vn:3001",
            "https://crm.erg.edu.vn:3001",
            "https://lcms.erg.edu.vn:3001",
            "https://lms.erg.edu.vn:3001",
            "https://elearning.erg.edu.vn:3001"
    };

    private static final String[] REQUIRED_ALLOWED_HEADERS = {
            "Accept",
            "Authorization",
            "Content-Type",
            "X-Tenant-ID",
            "X-Request-ID",
            "X-Trace-ID",
            "X-Idempotency-Key",
            "X-Portal"
    };

    private static final String[] BFF_ALLOWED_HEADERS = {
            "Accept",
            "Content-Type",
            "X-Request-ID",
            "X-Trace-ID",
            "X-Idempotency-Key",
            "X-XSRF-TOKEN"
    };

    private final String[] allowedOrigins;
    private final String[] allowedMethods;
    private final String[] allowedHeaders;
    private final String[] exposedHeaders;
    private final boolean allowCredentials;
    private final long maxAge;
    private final boolean bffEnabled;

    @Autowired
    public CorsConfig(
            Environment environment,
            ErgHttpProperties httpProperties,
            com.erg.ergspringboot.platform.security.ErgAuthProperties authProperties) {
        this(environment,
                httpProperties.getCors().getAllowedOrigins(),
                httpProperties.getCors().getAllowedMethods(),
                httpProperties.getCors().getAllowedHeaders(),
                httpProperties.getCors().getExposedHeaders(),
                httpProperties.getCors().isAllowCredentials(),
                httpProperties.getCors().getMaxAge(),
                authProperties.isBffEnabled());
    }

    /** Compatibility constructor for non-BFF callers and focused tests. */
    public CorsConfig(
            Environment environment,
            String allowedOrigins,
            String allowedMethods,
            String allowedHeaders,
            String exposedHeaders,
            boolean allowCredentials,
            long maxAge
    ) {
        this(environment, allowedOrigins, allowedMethods, allowedHeaders, exposedHeaders,
                allowCredentials, maxAge, false);
    }

    public CorsConfig(
            Environment environment,
            String allowedOrigins,
            String allowedMethods,
            String allowedHeaders,
            String exposedHeaders,
            boolean allowCredentials,
            long maxAge,
            boolean bffEnabled
    ) {
        boolean productionProfile = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        // In prod, allowed origins come solely from configuration so the baked-in dev origins
        // (including plaintext http://) cannot silently widen the allowlist. Outside prod they
        // are merged in for local convenience.
        this.allowedOrigins = productionProfile
                ? csv(allowedOrigins)
                : mergeCsv(allowedOrigins, REQUIRED_ALLOWED_ORIGINS);
        this.allowedMethods = csv(allowedMethods);
        this.bffEnabled = bffEnabled;
        String configuredHeaders = bffEnabled ? stripBffForbiddenHeaders(allowedHeaders) : allowedHeaders;
        this.allowedHeaders = mergeCsv(configuredHeaders, bffEnabled ? BFF_ALLOWED_HEADERS : REQUIRED_ALLOWED_HEADERS);
        this.exposedHeaders = csv(exposedHeaders);
        this.allowCredentials = allowCredentials;
        this.maxAge = maxAge <= 0 ? TimeUnit.HOURS.toSeconds(1) : maxAge;
        rejectWildcardWithCredentials(this.allowedOrigins, this.allowCredentials);
        rejectPlaintextOriginsWithCredentials(this.allowedOrigins, this.allowCredentials, productionProfile);
    }

    private String stripBffForbiddenHeaders(String value) {
        Set<String> forbidden = Set.of(
                "authorization", "x-tenant-id", "x-portal", "x-portals",
                "x-user-id", "x-session-id", "x-roles", "x-permissions",
                "x-denied-permissions", "x-account-type", "x-access-level",
                "x-managed-educationunit-ids", "x-managed-education-unit-ids",
                "x-managed-center-ids", "x-managed-class-ids", "x-current-educationunit-id",
                "x-current-education-unit-id", "x-current-center-id", "x-educationunit-id",
                "x-education-unit-id", "x-center-id", "x-current-class-id", "x-class-id");
        return Arrays.stream(csv(value))
                .filter(header -> !forbidden.contains(header.toLowerCase(java.util.Locale.ROOT)))
                .collect(Collectors.joining(","));
    }

    String[] allowedHeadersForTest() {
        return allowedHeaders.clone();
    }

    // A wildcard origin with credentials is forbidden by the CORS spec and lets any site read
    // authenticated responses; fail closed at startup rather than silently downgrading security.
    private static void rejectWildcardWithCredentials(String[] origins, boolean allowCredentials) {
        if (!allowCredentials) {
            return;
        }
        for (String origin : origins) {
            if ("*".equals(origin) || "*/".equals(origin) || origin.contains("*")) {
                throw new IllegalStateException(
                        "CORS misconfiguration: wildcard origin '" + origin
                                + "' cannot be combined with allow-credentials=true");
            }
        }
    }

    // In prod, a plaintext http:// origin with credentials lets a network attacker (MITM on the
    // unencrypted origin) read authenticated responses. Fail closed at startup so a stale env var
    // cannot silently ship an insecure allowlist.
    private static void rejectPlaintextOriginsWithCredentials(
            String[] origins, boolean allowCredentials, boolean productionProfile) {
        if (!allowCredentials || !productionProfile) {
            return;
        }
        for (String origin : origins) {
            if (origin.startsWith("http://")) {
                throw new IllegalStateException(
                        "CORS misconfiguration: plaintext origin '" + origin
                                + "' cannot be combined with allow-credentials=true in the prod profile");
            }
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods(allowedMethods)
                .allowedHeaders(allowedHeaders)
                .exposedHeaders(exposedHeaders)
                .allowCredentials(allowCredentials)
                .maxAge(maxAge);
    }

    private String[] csv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toArray(String[]::new);
    }

    private String[] mergeCsv(String value, String[] requiredValues) {
        Set<String> merged = new LinkedHashSet<>(Arrays.asList(csv(value)));
        merged.addAll(Arrays.asList(requiredValues));
        return merged.toArray(String[]::new);
    }
}
