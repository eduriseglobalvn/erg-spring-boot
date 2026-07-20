package com.erg.ergspringboot.platform.security;

import java.util.List;
import java.util.function.Supplier;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

@Component
public class PermissionAuthorizationManager {

    private final ErgPolicyService policyService;

    public PermissionAuthorizationManager(ErgPolicyService policyService) {
        this.policyService = policyService;
    }

    public AuthorizationDecision require(
            String permission,
            Supplier<? extends Authentication> authenticationSupplier,
            RequestAuthorizationContext context
    ) {
        Authentication authentication = authenticationSupplier.get();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken || !authentication.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }
        if (!(authentication.getPrincipal() instanceof ErgPrincipal principal)) {
            return new AuthorizationDecision(false);
        }
        String portal = SecurityUtils.portalFromPath(context.getRequest().getRequestURI());
        return new AuthorizationDecision(policyService.isAllowed(principal, permission, portal, principal.tenantId()));
    }

    public AuthorizationDecision requireAny(
            Supplier<? extends Authentication> authenticationSupplier,
            RequestAuthorizationContext context,
            List<String> permissions
    ) {
        Authentication authentication = authenticationSupplier.get();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken || !authentication.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }
        if (!(authentication.getPrincipal() instanceof ErgPrincipal principal)) {
            return new AuthorizationDecision(false);
        }
        String portal = SecurityUtils.portalFromPath(context.getRequest().getRequestURI());
        return new AuthorizationDecision(policyService.isAllowedAny(principal, portal, principal.tenantId(), permissions));
    }
}
