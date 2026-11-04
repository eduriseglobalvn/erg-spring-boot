package com.erg.ergspringboot.learningstandards.internal;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Tenant and permission checks for SCORM/xAPI/LTI commands and queries. */
final class LearningStandardsAccessGuard {

    private LearningStandardsAccessGuard() {
    }

    static String tenant(UserAccessContext access) {
        if (access == null || access.userId() == null || access.userId().isBlank()) {
            throw new ForbiddenException("authenticated access context is required");
        }
        return AuthenticatedTenant.require(access.tenantId());
    }

    static void ensureManage() {
        if (!permission("learning.standards.manage")) throw new ForbiddenException("learning standards management is required");
    }

    static void ensureRead() {
        if (!permission("learning.standards.read") && !permission("learning.standards.manage")) {
            throw new ForbiddenException("learning standards read permission is required");
        }
    }

    static void ensureLaunch() {
        if (!permission("learning.standards.launch") && !permission("learning.standards.manage")) {
            throw new ForbiddenException("learning standards launch permission is required");
        }
    }

    private static boolean permission(String expected) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof ErgPrincipal principal)) return false;
        if (principal.deniedPermissions().stream()
                .map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.equals("*") || value.equals(expected))) return false;
        return principal.permissions().stream().map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.equals("*") || value.equals(expected));
    }
}
