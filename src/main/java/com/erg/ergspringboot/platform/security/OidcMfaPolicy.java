package com.erg.ergspringboot.platform.security;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OidcMfaPolicy {
    private static final Set<String> PRIVILEGED_MARKERS = Set.of(
            "admin", "teacher", "instructor", "employee", "manager", "staff");
    private static final Set<String> MFA_METHODS = Set.of(
            "mfa", "otp", "totp", "webauthn", "passkey", "hwk", "swk");

    public boolean satisfied(List<String> roles, Map<String, Object> claims) {
        if (!privileged(roles)) return true;
        Object amr = claims == null ? null : claims.get("amr");
        if (amr instanceof Collection<?> methods && methods.stream()
                .map(String::valueOf).map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(MFA_METHODS::contains)) return true;
        String acr = claims == null ? "" : String.valueOf(claims.getOrDefault("acr", ""));
        return acr.toLowerCase(Locale.ROOT).contains("mfa") || acr.matches(".*(?:loa|aal)[2-9].*");
    }

    private boolean privileged(List<String> roles) {
        return roles != null && roles.stream().filter(java.util.Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(role -> PRIVILEGED_MARKERS.stream().anyMatch(role::contains));
    }
}
