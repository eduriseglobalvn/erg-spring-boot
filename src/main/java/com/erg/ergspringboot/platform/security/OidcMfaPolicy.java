package com.erg.ergspringboot.platform.security;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OidcMfaPolicy {
    /** Holding the admin workspace (or the platform-wide wildcard) is what makes an account privileged. */
    private static final Set<String> PRIVILEGED_PERMISSIONS = Set.of("admin.workspace.access", "*");
    private static final Set<String> MFA_METHODS = Set.of(
            "mfa", "otp", "totp", "webauthn", "passkey", "hwk", "swk");

    /** {@code permissions} are the account's effective granted permissions, not its role names. */
    public boolean satisfied(Set<String> permissions, Map<String, Object> claims) {
        if (!privileged(permissions)) return true;
        Object amr = claims == null ? null : claims.get("amr");
        if (amr instanceof Collection<?> methods && methods.stream()
                .map(String::valueOf).map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(MFA_METHODS::contains)) return true;
        String acr = claims == null ? "" : String.valueOf(claims.getOrDefault("acr", ""));
        return acr.toLowerCase(Locale.ROOT).contains("mfa") || acr.matches(".*(?:loa|aal)[2-9].*");
    }

    private boolean privileged(Set<String> permissions) {
        return permissions != null && permissions.stream().filter(java.util.Objects::nonNull)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(PRIVILEGED_PERMISSIONS::contains);
    }
}
