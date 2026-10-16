package com.erg.ergspringboot.platform.security;

import com.erg.ergspringboot.authorization.AuthorizationOperations;
import java.util.Arrays;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Bridges typed method-security annotations to the canonical deny-aware policy. */
@Component("methodAuthorization")
public class MethodAuthorizationEvaluator {

    private final AuthorizationOperations authorization;

    public MethodAuthorizationEvaluator(AuthorizationOperations authorization) {
        this.authorization = authorization;
    }

    public boolean isAllowedAny(Authentication authentication, String... permissions) {
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof ErgPrincipal principal)
                || principal.tenantId() == null
                || principal.tenantId().isBlank()) {
            return false;
        }
        return authorization.isAllowedAny(
                principal,
                null,
                principal.tenantId(),
                permissions == null ? java.util.List.of() : Arrays.asList(permissions));
    }
}
