package com.erg.ergspringboot.platform.security;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

// Fail closed at startup: header-only auth outside tests would let anyone forge a principal via
// X-User-ID/X-Permissions. Allow-list the test profile instead of deny-listing prod, so a public
// staging/dev box with a stray env var cannot boot either. Kept out of ErgSecurityProperties so
// that class stays a plain @ConfigurationProperties bean (field-bound, no-arg constructable in tests).
@Component
public class TrustedHeaderAuthProductionGuard {

    private final ErgSecurityProperties properties;
    private final Environment environment;

    public TrustedHeaderAuthProductionGuard(ErgSecurityProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void rejectTrustedHeaderAuthOutsideTests() {
        if (properties.isTrustedHeaderAuthEnabled()
                && !Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            throw new IllegalStateException(
                    "erg.security.trusted-header-auth-enabled may only be true in the test profile");
        }
    }
}
