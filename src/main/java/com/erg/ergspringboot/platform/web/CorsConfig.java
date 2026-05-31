package com.erg.ergspringboot.platform.web;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
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

    private final String[] allowedOrigins;
    private final String[] allowedMethods;
    private final String[] allowedHeaders;
    private final String[] exposedHeaders;
    private final boolean allowCredentials;
    private final long maxAge;

    public CorsConfig(
            @Value("${erg.http.cors.allowed-origins:}") String allowedOrigins,
            @Value("${erg.http.cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}") String allowedMethods,
            @Value("${erg.http.cors.allowed-headers:Accept,Authorization,Content-Type,X-Tenant-ID,X-Request-ID,X-Trace-ID,X-Idempotency-Key}") String allowedHeaders,
            @Value("${erg.http.cors.exposed-headers:X-Request-ID,X-Trace-ID}") String exposedHeaders,
            @Value("${erg.http.cors.allow-credentials:true}") boolean allowCredentials,
            @Value("${erg.http.cors.max-age:86400}") long maxAge
    ) {
        this.allowedOrigins = mergeCsv(allowedOrigins, REQUIRED_ALLOWED_ORIGINS);
        this.allowedMethods = csv(allowedMethods);
        this.allowedHeaders = mergeCsv(allowedHeaders, REQUIRED_ALLOWED_HEADERS);
        this.exposedHeaders = csv(exposedHeaders);
        this.allowCredentials = allowCredentials;
        this.maxAge = maxAge <= 0 ? TimeUnit.HOURS.toSeconds(1) : maxAge;
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
