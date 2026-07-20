package com.erg.ergspringboot.authorization;

import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.util.List;
import org.springframework.stereotype.Service;

/** Public access-decision facade; web security remains an adapter. */
@Service
public class AuthorizationOperations {
    private final ErgPolicyService policy;

    public AuthorizationOperations(ErgPolicyService policy) {
        this.policy = policy;
    }

    public boolean isAllowed(ErgPrincipal principal, String permission, String portal, String tenantId) {
        return policy.isAllowed(principal, permission, portal, tenantId);
    }

    public boolean isAllowedAny(ErgPrincipal principal, String portal, String tenantId, List<String> permissions) {
        return policy.isAllowedAny(principal, portal, tenantId, permissions);
    }
}
