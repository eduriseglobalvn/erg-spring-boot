package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.authorization.catalog.PermissionCatalog;
import com.erg.ergspringboot.authorization.catalog.PermissionKind;
import jakarta.servlet.Filter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Spec §4.2(d): proves "view-only" really is view-only. Every REST write route is probed with a principal that
 * holds exactly one XEM permission; if the URL rule and the method annotation both let it through, a read grant
 * can change data. GraphQL mutations check scope inside the controllers and are covered by their own tests.
 */
@ActiveProfiles({"test", "prototype"})
@SpringBootTest(properties = {
        "erg.auth.bff-enabled=true",
        "erg.process-local.enabled=true",
        "spring.security.oauth2.client.registration.erg-api.provider=keycloak",
        "spring.security.oauth2.client.registration.erg-api.client-id=catalog-test",
        "spring.security.oauth2.client.registration.erg-api.client-secret=catalog-test",
        "spring.security.oauth2.client.registration.erg-api.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.erg-api.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "spring.security.oauth2.client.registration.erg-api.scope=openid,profile,email",
        "spring.security.oauth2.client.provider.keycloak.authorization-uri=http://localhost/catalog/auth",
        "spring.security.oauth2.client.provider.keycloak.token-uri=http://localhost/catalog/token",
        "spring.security.oauth2.client.provider.keycloak.jwk-set-uri=http://localhost/catalog/jwks",
        "spring.security.oauth2.client.provider.keycloak.user-info-uri=http://localhost/catalog/userinfo",
        "spring.security.oauth2.client.provider.keycloak.user-name-attribute=sub"
})
class EndpointPermissionKindTest {

    private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

    /**
     * Writes a read-only user may legitimately make. Each entry is checked elsewhere: the handler limits the call to
     * the caller's own data, or the POST only starts a read.
     */
    private static final Set<String> SELF_SERVICE = Set.of(
            "/api/v1/auth/logout", // ends the caller's own sessions (also covers logout-all)
            "/api/v1/notifications/", // own inbox only: every call is scoped to principal.userId()
            "/api/v1/notification-devices", // own devices, gated by profile.self
            "/api/v1/profiles", // ProfileController: own profile, or users.manage for someone else's
            "/api/v1/reports/jobs/assignment-progress"); // enqueues a report: @ReportingRead, a read in effect

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private List<SecurityFilterChain> chains;

    @Test
    void noReadPermissionOpensAWriteRoute() {
        AuthorizationManager<jakarta.servlet.http.HttpServletRequest> urlRules = urlRules();
        List<String> readPermissions = PermissionCatalog.all().stream()
                .filter(def -> def.kind() == PermissionKind.XEM).map(PermissionCatalog.PermissionDef::name).toList();
        Set<String> violations = new TreeSet<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
            List<String> writeMethods = methods.isEmpty()
                    ? List.of("POST")
                    : methods.stream().filter(m -> m != RequestMethod.GET && m != RequestMethod.HEAD
                            && m != RequestMethod.OPTIONS).map(Enum::name).toList();
            if (writeMethods.isEmpty() || entry.getKey().getPathPatternsCondition() == null) {
                continue;
            }
            Profile profile = AnnotatedElementUtils.findMergedAnnotation(entry.getValue().getBeanType(), Profile.class);
            if (profile != null && List.of(profile.value()).contains("prototype")) {
                continue; // prototype-only controllers are never deployed (prod and dev profiles)
            }
            List<String> methodPermissions = methodPermissions(entry.getValue());
            for (String pattern : entry.getKey().getPathPatternsCondition().getPatternValues()) {
                if (!pattern.startsWith("/api/") || pattern.equals("/api/v1/graphql")
                        || SELF_SERVICE.stream().anyMatch(pattern::startsWith)) {
                    continue;
                }
                String path = pattern.replaceAll("\\{[^}]+}", "x").replace("**", "x");
                for (String httpMethod : writeMethods) {
                    if (urlRules.authorize(EndpointPermissionKindTest::anonymous,
                            new MockHttpServletRequest(httpMethod, path)).isGranted()) {
                        continue; // public by design: permitAll routes carry @ReviewedPublicEndpoint
                    }
                    for (String permission : readPermissions) {
                        UsernamePasswordAuthenticationToken auth = auth(permission);
                        boolean urlGranted = urlRules.authorize(() -> auth, new MockHttpServletRequest(httpMethod, path))
                                .isGranted();
                        boolean methodGranted = methodPermissions == null || methodPermissions.contains(permission);
                        if (urlGranted && methodGranted) {
                            // one line per route: listing every XEM permission would truncate the report
                            violations.add(httpMethod + " " + pattern);
                        }
                    }
                }
            }
        }

        assertThat(violations).as("write routes reachable with a single XEM permission").isEmpty();
    }

    /** Permissions named by a typed {@code isAllowedAny} annotation; null when the method has no such gate. */
    private static List<String> methodPermissions(HandlerMethod handler) {
        PreAuthorize pre = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), PreAuthorize.class);
        if (pre == null) {
            pre = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), PreAuthorize.class);
        }
        if (pre == null || !pre.value().contains("isAllowedAny")) {
            return null;
        }
        List<String> names = new ArrayList<>();
        Matcher matcher = QUOTED.matcher(pre.value());
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private AuthorizationManager<jakarta.servlet.http.HttpServletRequest> urlRules() {
        for (SecurityFilterChain chain : chains) {
            for (Filter filter : chain.getFilters()) {
                if (filter instanceof AuthorizationFilter authorization) {
                    return authorization.getAuthorizationManager();
                }
            }
        }
        throw new IllegalStateException("no AuthorizationFilter in the security filter chains");
    }

    private static AnonymousAuthenticationToken anonymous() {
        return new AnonymousAuthenticationToken("probe", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    }

    private static UsernamePasswordAuthenticationToken auth(String permission) {
        ErgPrincipal principal = new ErgPrincipal("probe", "session-probe", "erg", List.of("probe"),
                List.of(permission), List.of(), List.of("admin"), "erg", "admin", Map.of());
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }
}
