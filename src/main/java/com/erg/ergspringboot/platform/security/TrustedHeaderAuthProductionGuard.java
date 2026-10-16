package com.erg.ergspringboot.platform.security;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

// Fail closed at startup: header-only auth in prod would let anyone forge a principal via
// X-User-ID/X-Permissions. A stray env var or misapplied test profile must not boot. Kept out of
// ErgSecurityProperties so that class stays a plain @ConfigurationProperties bean (field-bound,
// no-arg constructable in tests).
@Component
public class TrustedHeaderAuthProductionGuard {

    private final ErgSecurityProperties properties;
    private final Environment environment;

    public TrustedHeaderAuthProductionGuard(ErgSecurityProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void rejectTrustedHeaderAuthInProduction() {
        if (properties.isTrustedHeaderAuthEnabled()
                && Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            throw new IllegalStateException(
                    "erg.security.trusted-header-auth-enabled must be false in the prod profile");
        }
    }
}
