package com.erg.ergspringboot.controller.auth;

import com.erg.ergspringboot.common.response.auth.AuthResponses.BffScopeResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.BffSessionResponse;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.AccessScopeSet;
import com.erg.ergspringboot.platform.security.AccessScopeAssembler;
import com.erg.ergspringboot.platform.security.LegacyNamespacePolicy;
import com.erg.ergspringboot.platform.security.SecurityUtils;
import com.erg.ergspringboot.platform.security.WorkspaceCapabilityMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
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
    /** Canonical workspace names; never subject to the legacy-alias sunset. */
    private static final Set<String> CANONICAL_PORTALS = Set.of("admin", "teacher", "student", "crm");
    private static final Set<String> LEGACY_PORTALS = Set.of("lcms", "lms", "elearning");
    static final String DEFAULT_PORTAL = "teacher";
    private final Clock clock;
    private final LocalDate legacySunset;
    private MeterRegistry meters;

    public OidcBffController() {
        this(Clock.systemUTC(), null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OidcBffController(
            @org.springframework.beans.factory.annotation.Value("${erg.auth.legacy-portal-sunset:}") String legacySunset) {
        this(Clock.systemUTC(), legacySunset == null || legacySunset.isBlank() ? null : LocalDate.parse(legacySunset.trim()));
    }

    OidcBffController(Clock clock) {
        this(clock, null);
    }

    /** @param legacySunset overrides the built-in legacy alias sunset; {@code null} keeps the policy default. */
    OidcBffController(Clock clock, LocalDate legacySunset) {
        this.clock = clock;
        this.legacySunset = legacySunset;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setMeterRegistry(MeterRegistry meters) {
        this.meters = meters;
    }

    @GetMapping("/login")
    public ResponseEntity<Void> login(
            @RequestParam(defaultValue = DEFAULT_PORTAL) String portal,
            @RequestParam(defaultValue = "/") String returnTo,
            HttpSession session
    ) {
        String sanitizedPortal = sanitizePortal(portal);
        var alias = CANONICAL_PORTALS.contains(sanitizedPortal)
                ? java.util.Optional.<LegacyNamespacePolicy.LegacyAlias>empty()
                : LegacyNamespacePolicy.alias(sanitizedPortal).map(this::withConfiguredSunset);
        if (alias.isPresent()) {
            if (LocalDate.now(clock).isAfter(alias.get().sunset())) {
                return ResponseEntity.status(HttpStatus.GONE)
                        .header("Deprecation", "true")
                        .header("Sunset", sunsetHeader(alias.get().sunset()))
                        .build();
            }
            recordLegacyAlias(alias.get());
        }
        session.setAttribute(PORTAL_SESSION_KEY, sanitizedPortal);
        session.setAttribute(RETURN_TO_SESSION_KEY, sanitizeReturnTo(returnTo));
        var response = ResponseEntity.status(302)
                .location(URI.create("/oauth2/authorization/erg-api"));
        alias.ifPresent(metadata -> response
                .header("Deprecation", "true")
                .header("Sunset", sunsetHeader(metadata.sunset()))
                .header("Link", "<" + metadata.successor() + ">; rel=\"successor-version\""));
        return response.build();
    }

    @GetMapping("/session")
    public BffSessionResponse session() {
        return toSessionResponse(SecurityUtils.requirePrincipal());
    }

    static BffSessionResponse toSessionResponse(ErgPrincipal principal) {
        List<String> roles = safe(principal.roles());
        List<String> permissions = safe(principal.permissions());
        List<String> deniedPermissions = safe(principal.deniedPermissions());
        List<String> portals = WorkspaceCapabilityMapper.legacyPortalsFor(
                safe(principal.portals()), roles, permissions);
        List<String> workspaces = WorkspaceCapabilityMapper.canonicalWorkspaces(
                safe(principal.portals()), roles, permissions);
        AccessScopeSet scopes = AccessScopeAssembler.from(principal);
        return new BffSessionResponse(
                principal.userId(),
                principal.tenantId(),
                roles,
                permissions,
                deniedPermissions,
                portals,
                workspaces,
                principal.accountType(),
                principal.accessLevel(),
                safeProfile(principal.attributes()),
                new BffScopeResponse(
                        scopes.managedIds(AccessScopeSet.EDUCATION_UNIT),
                        scopes.managedIds(AccessScopeSet.CLASS),
                        scopes.currentId(AccessScopeSet.EDUCATION_UNIT).orElse(null),
                        scopes.currentId(AccessScopeSet.CLASS).orElse(null),
                        scopes.fingerprint()
                )
        );
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of("headerName", csrfToken.getHeaderName(), "token", csrfToken.getToken());
    }

    /**
     * Compatibility helper for callers that invoke the BFF controller directly.
     * The HTTP endpoint is owned by Spring Security's logout filter so it cannot
     * collide with the stateless AuthController mapping when both route sets are
     * present in a catalog/test context.
     */
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    static String sanitizeReturnTo(String returnTo) {
        if (returnTo == null || !returnTo.startsWith("/") || returnTo.startsWith("//")) return "/";
        return returnTo;
    }

    static String sanitizePortal(String portal) {
        String normalized = portal == null ? DEFAULT_PORTAL : portal.trim().toLowerCase(Locale.ROOT);
        return CANONICAL_PORTALS.contains(normalized) || LEGACY_PORTALS.contains(normalized)
                ? normalized : DEFAULT_PORTAL;
    }

    private LegacyNamespacePolicy.LegacyAlias withConfiguredSunset(LegacyNamespacePolicy.LegacyAlias alias) {
        return legacySunset == null ? alias : new LegacyNamespacePolicy.LegacyAlias(
                alias.alias(), alias.canonicalWorkspace(), legacySunset, alias.successor());
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static Map<String, Object> safeProfile(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) return Map.of();
        Map<String, Object> safe = new LinkedHashMap<>();
        attributes.forEach((key, value) -> {
            String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
            if (normalized.contains("token")
                    || normalized.contains("secret")
                    || normalized.contains("credential")
                    || normalized.contains("authorization")
                    || normalized.contains("cookie")) {
                return;
            }
            safe.put(key, value);
        });
        return Map.copyOf(safe);
    }

    private void recordLegacyAlias(LegacyNamespacePolicy.LegacyAlias alias) {
        if (meters == null) return;
        Counter.builder("erg.auth.legacy_workspace_alias.requests")
                .description("BFF login requests using a legacy workspace alias")
                .tag("alias", alias.alias())
                .tag("workspace", alias.canonicalWorkspace())
                .register(meters)
                .increment();
    }

    private String sunsetHeader(LocalDate sunset) {
        return sunset.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);
    }
}
