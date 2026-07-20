package com.erg.ergspringboot.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "erg.security")
public class ErgSecurityProperties {

    /**
     * Trusted-header authentication builds a principal directly from request headers
     * (X-User-ID, X-Permissions, X-Portals, ...). This is a test/dev convenience and a
     * production vulnerability, so it is DISABLED by default. Enable it only in trusted,
     * non-public contexts (e.g. the test profile). Production must rely on Bearer tokens.
     */
    private boolean trustedHeaderAuthEnabled = false;

    public boolean isTrustedHeaderAuthEnabled() {
        return trustedHeaderAuthEnabled;
    }

    public void setTrustedHeaderAuthEnabled(boolean trustedHeaderAuthEnabled) {
        this.trustedHeaderAuthEnabled = trustedHeaderAuthEnabled;
    }
}
