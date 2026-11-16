package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.authorization.AuthorizationOperations;
import com.erg.ergspringboot.authorization.authorization.UsersActivityRead;
import com.erg.ergspringboot.authorization.authorization.UsersRead;
import com.erg.ergspringboot.controller.authorization.AuthorizationController;
import com.erg.ergspringboot.controller.users.AdminEmployeeController;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Route-level permission rules: no portal-by-path gate, read/manage split, audit and activity routes. */
class SecurityRouteMatrixTest {

    private static final Path SECURITY_CONFIG = Path.of(
            "src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java");

    private final PermissionAuthorizationManager manager =
            new PermissionAuthorizationManager(new AuthorizationOperations(new ErgPolicyService()));

    @Test
    void pdtCanGetLmsEndpointWithoutTeacherPortal() {
        // Admin-workspace holder with no teacher workspace: the path no longer demands the "lms" portal.
        ErgPrincipal pdt = principal(List.of("pdt_operator"), List.of("lms.class.read", "admin.workspace.access"));

        AuthorizationDecision decision = manager.require(
                "lms.class.read", () -> auth(pdt), context("GET", "/api/v1/lms/classes"));

        assertThat(decision.isGranted()).isTrue();
    }

    @Test
    void operationsCanGetEmployeesWithUsersRead() throws Exception {
        ErgPrincipal operations = principal(List.of("operations"), List.of("users.read"));
        List<String> read = List.of("users.read", "users.manage", "*");

        assertThat(manager.requireAny(() -> auth(operations), context("GET", "/api/v1/admin/users"), read)
                .isGranted()).isTrue();
        assertThat(manager.require("users.manage", () -> auth(operations), context("POST", "/api/v1/admin/users"))
                .isGranted()).isFalse();

        assertThat(Files.readString(SECURITY_CONFIG).replace("\r\n", "\n"))
                .contains("HttpMethod.GET, \"/api/v1/users\", \"/api/v1/users/**\", \"/api/v1/admin/users\", \"/api/v1/admin/users/**\")\n"
                        + "                        .access((auth, context) -> permissions.requireAny(auth, context, "
                        + "List.of(\"users.read\", \"users.manage\", \"*\")))");
        assertThat(permissionsOf(UsersRead.class)).contains("'users.read'", "'users.manage'");
    }

    @Test
    void auditRequiresAuditRead() throws Exception {
        String source = Files.readString(SECURITY_CONFIG).replace("\r\n", "\n");
        int auditRoute = source.indexOf(".requestMatchers(\"/api/v1/audit/**\")");

        assertThat(auditRoute).isGreaterThan(0);
        assertThat(source.substring(auditRoute, auditRoute + 200)).contains("audit.read");
        assertThat(source).doesNotContain("\"/api/v1/operations/**\", \"/api/v1/audit/**\"");
        assertThat(source.indexOf("\"/api/v1/audit/export\"")).isLessThan(auditRoute);

        ErgPrincipal reader = principal(List.of("director"), List.of("audit.read"));
        List<String> needed = List.of("audit.read", "audit.manage");
        assertThat(manager.requireAny(() -> auth(reader), context("GET", "/api/v1/audit/events"), needed)
                .isGranted()).isTrue();
        assertThat(manager.requireAny(() -> auth(reader), context("GET", "/api/v1/audit/export"),
                List.of("audit.export", "audit.manage")).isGranted()).isFalse();
        ErgPrincipal nobody = principal(List.of("pdt_operator"), List.of("users.read"));
        assertThat(manager.requireAny(() -> auth(nobody), context("GET", "/api/v1/audit/events"), needed)
                .isGranted()).isFalse();
    }

    @Test
    void activityRequiresUsersActivityRead() {
        for (Method method : AdminEmployeeController.class.getDeclaredMethods()) {
            GetMapping get = method.getAnnotation(GetMapping.class);
            if (get == null) {
                continue;
            }
            String paths = Arrays.toString(get.value());
            boolean activity = paths.contains("activity") || paths.contains("security-events")
                    || paths.contains("sessions") || paths.contains("security-feed");
            assertThat(method.isAnnotationPresent(UsersActivityRead.class))
                    .as(method.getName() + " " + paths)
                    .isEqualTo(activity);
        }
        assertThat(permissionsOf(UsersActivityRead.class)).contains("'users.activity.read'")
                .doesNotContain("users.manage");
    }

    @Test
    void roleCrudEndpointsGone() {
        for (Method method : AuthorizationController.class.getDeclaredMethods()) {
            String name = method.getName();
            assertThat(name).isNotIn("createRole", "updateRole", "deleteRole", "deletePermissionOverride",
                    "permissionOverrides");
            assertThat(method.getAnnotation(RequestMapping.class)).as(name).isNull();
        }
        String source = Files.exists(SECURITY_CONFIG) ? read() : "";
        assertThat(source).doesNotContain("rbac.role.create").doesNotContain("rbac.role.update")
                .doesNotContain("rbac.role.delete").doesNotContain("permission-overrides");
    }

    private static String read() {
        try {
            return Files.readString(SECURITY_CONFIG);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String permissionsOf(Class<? extends java.lang.annotation.Annotation> annotation) {
        return annotation.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value();
    }

    private static UsernamePasswordAuthenticationToken auth(ErgPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    private static RequestAuthorizationContext context(String method, String path) {
        return new RequestAuthorizationContext(new MockHttpServletRequest(method, path));
    }

    private static ErgPrincipal principal(List<String> roles, List<String> permissions) {
        return new ErgPrincipal("actor-1", "session-1", "erg", roles, permissions, List.of(),
                List.of("admin"), "erg", "admin", Map.of());
    }
}
