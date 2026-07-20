package com.erg.ergspringboot.platform.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class GraphQlRequestContextFactoryTest {

    private final GraphQlRequestContextFactory factory = new GraphQlRequestContextFactory();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void buildsContextFromVerifiedPrincipalAndTreatsHeadersAsRequestedContextOnly() {
        authenticate(principal("tenant-a", List.of("lms")));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("opaque-token");
        headers.set("X-Portal", "LMS");
        headers.set("X-Tenant-ID", "tenant-a");
        headers.set("X-Request-ID", "request-1");
        headers.set("X-Current-Class-ID", "class-2");

        GraphQlRequestContext context = factory.from(headers, "ClassWorkspace");

        assertThat(context.userId()).isEqualTo("teacher-1");
        assertThat(context.tenantId()).isEqualTo("tenant-a");
        assertThat(context.requestedTenantId()).isEqualTo("tenant-a");
        assertThat(context.requestedPortal()).isEqualTo("lms");
        assertThat(context.requestId()).isEqualTo("request-1");
        assertThat(context.operationName()).isEqualTo("ClassWorkspace");
        assertThat(context.authorizationPresent()).isTrue();
        assertThat(context.access().managedEducationUnitIds()).containsExactly("school-a");
        assertThat(context.access().managedClassIds()).containsExactly("class-1", "class-2");
        assertThat(context.access().currentClassId()).isEqualTo("class-2");
    }

    @Test
    void rejectsTenantHeaderThatDoesNotMatchPrincipalTenant() {
        authenticate(principal("tenant-a", List.of("lms")));
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", "tenant-b");

        assertThatThrownBy(() -> factory.from(headers, "LmsShell"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    void rejectsPortalHeaderThatPrincipalCannotAccess() {
        authenticate(principal("tenant-a", List.of("lms")));
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Portal", "crm");

        assertThatThrownBy(() -> factory.from(headers, "CrmShell"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("portal");
    }

    @Test
    void rejectsMissingVerifiedPrincipal() {
        assertThatThrownBy(() -> factory.from(new HttpHeaders(), "LmsShell"))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessageContaining("verified principal");
    }

    private void authenticate(ErgPrincipal principal) {
        List<SimpleGrantedAuthority> authorities = principal.permissions().stream()
                .map(permission -> new SimpleGrantedAuthority("perm:" + permission))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities)
        );
    }

    private ErgPrincipal principal(String tenantId, List<String> portals) {
        return new ErgPrincipal(
                "teacher-1",
                "session-1",
                tenantId,
                List.of("teacher"),
                List.of("lms.class.read"),
                List.of(),
                portals,
                "teacher",
                "school",
                Map.of(
                        "managedEducationUnitIds", List.of("school-a"),
                        "managedClassIds", List.of("class-1", "class-2"),
                        "currentClassId", "class-1"
                )
        );
    }
}
