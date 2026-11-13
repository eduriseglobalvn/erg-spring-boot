package com.erg.ergspringboot.notifications;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.UnauthorizedException;
import com.erg.ergspringboot.platform.security.AuthorizationCapabilityPolicy;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class NotificationSecuritySupport {

    public ErgPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ErgPrincipal principal) {
            return principal;
        }
        throw new UnauthorizedException("Missing authenticated user.");
    }

    public String verifiedPortal(ErgPrincipal principal, String requestedPortal) {
        String portal = requestedPortal == null || requestedPortal.isBlank() ? "lms" : requestedPortal.trim().toLowerCase();
        // A learner's session portal is "elearning", but its assignment notices are stored under "lms" (STU-04);
        // the same workspace rule as the API guard lets the student workspace into the lms family.
        if (principal.portals().contains("*") || principal.portals().contains(portal)
                || new AuthorizationCapabilityPolicy().hasWorkspace(principal, portal)) {
            return portal;
        }
        throw new ForbiddenException("Notification portal is not allowed for this session.");
    }
}
