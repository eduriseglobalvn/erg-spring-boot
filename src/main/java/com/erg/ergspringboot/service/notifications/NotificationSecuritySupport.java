package com.erg.ergspringboot.service.notifications;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.UnauthorizedException;
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
        if (principal.portals().contains("*") || principal.portals().contains(portal)) {
            return portal;
        }
        throw new ForbiddenException("Notification portal is not allowed for this session.");
    }
}
