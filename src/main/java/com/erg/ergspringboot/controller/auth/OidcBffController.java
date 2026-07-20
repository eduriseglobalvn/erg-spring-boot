package com.erg.ergspringboot.controller.auth;

import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.SecurityUtils;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "erg.auth.bff-enabled", havingValue = "true")
public class OidcBffController {

    static final String RETURN_TO_SESSION_KEY = "erg.auth.returnTo";
    static final String PORTAL_SESSION_KEY = "erg.auth.portal";
    private static final Set<String> PORTALS = Set.of("admin", "crm", "lcms", "lms", "elearning");

    @GetMapping("/login")
    public ResponseEntity<Void> login(
            @RequestParam(defaultValue = "lms") String portal,
            @RequestParam(defaultValue = "/") String returnTo,
            HttpSession session
    ) {
        session.setAttribute(PORTAL_SESSION_KEY, sanitizePortal(portal));
        session.setAttribute(RETURN_TO_SESSION_KEY, sanitizeReturnTo(returnTo));
        return ResponseEntity.status(302)
                .location(URI.create("/oauth2/authorization/erg-api"))
                .build();
    }

    @GetMapping("/session")
    public Map<String, Object> session() {
        ErgPrincipal principal = SecurityUtils.requirePrincipal();
        return Map.of(
                "userId", principal.userId(),
                "tenantId", principal.tenantId(),
                "roles", safe(principal.roles()),
                "permissions", safe(principal.permissions()),
                "portals", safe(principal.portals()),
                "accountType", principal.accountType(),
                "accessLevel", principal.accessLevel(),
                "profile", principal.attributes() == null ? Map.of() : principal.attributes()
        );
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of("headerName", csrfToken.getHeaderName(), "token", csrfToken.getToken());
    }

    static String sanitizeReturnTo(String returnTo) {
        if (returnTo == null || !returnTo.startsWith("/") || returnTo.startsWith("//")) return "/";
        return returnTo;
    }

    static String sanitizePortal(String portal) {
        String normalized = portal == null ? "lms" : portal.trim().toLowerCase();
        return PORTALS.contains(normalized) ? normalized : "lms";
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }
}
