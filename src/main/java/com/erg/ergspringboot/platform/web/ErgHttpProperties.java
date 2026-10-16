package com.erg.ergspringboot.platform.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Typed HTTP settings used by security-sensitive web adapters. */
@Component
@Validated
@ConfigurationProperties("erg.http")
public class ErgHttpProperties {

    private String trustedProxyCidrs = "";

    @Valid
    private final Cors cors = new Cors();

    public String getTrustedProxyCidrs() {
        return trustedProxyCidrs;
    }

    public void setTrustedProxyCidrs(String trustedProxyCidrs) {
        this.trustedProxyCidrs = trustedProxyCidrs == null ? "" : trustedProxyCidrs.trim();
    }

    public Cors getCors() {
        return cors;
    }

    public static final class Cors {
        private String allowedOrigins = "";
        private String allowedMethods = "GET,POST,PUT,DELETE,PATCH,OPTIONS";
        private String allowedHeaders = "Accept,Authorization,Content-Type,X-Tenant-ID,X-Request-ID,X-Trace-ID,X-Idempotency-Key";
        private String exposedHeaders = "X-Request-ID,X-Trace-ID,Deprecation,Sunset,Link,X-ERG-API-Owner";
        private boolean allowCredentials = true;
        @NotNull
        @Min(0)
        private Long maxAge = 86400L;

        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = value(allowedOrigins); }
        public String getAllowedMethods() { return allowedMethods; }
        public void setAllowedMethods(String allowedMethods) { this.allowedMethods = value(allowedMethods); }
        public String getAllowedHeaders() { return allowedHeaders; }
        public void setAllowedHeaders(String allowedHeaders) { this.allowedHeaders = value(allowedHeaders); }
        public String getExposedHeaders() { return exposedHeaders; }
        public void setExposedHeaders(String exposedHeaders) { this.exposedHeaders = value(exposedHeaders); }
        public boolean isAllowCredentials() { return allowCredentials; }
        public void setAllowCredentials(boolean allowCredentials) { this.allowCredentials = allowCredentials; }
        public Long getMaxAge() { return maxAge; }
        public void setMaxAge(Long maxAge) { this.maxAge = maxAge; }

        private static String value(String value) { return value == null ? "" : value.trim(); }
    }
}
