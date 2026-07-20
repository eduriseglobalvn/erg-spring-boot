package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

class UserAccessContextFailClosedTest {

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestWithoutVerifiedPrincipalIsRejected() {
        assertThatThrownBy(() -> UserAccessContext.from(new MockHttpServletRequest("GET", "/api/v1/lms/classes")))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    void systemActorContextIsExplicitAndScoped() {
        UserAccessContext context = SystemActorContext.forTask("outbox-dispatch", "tenant-1");

        assertThat(context.userId()).isEqualTo("system:outbox-dispatch");
        assertThat(context.tenantId()).isEqualTo("tenant-1");
        assertThat(context.managedEducationUnitIds()).containsExactly("*");
        assertThat(context.managedClassIds()).containsExactly("*");
    }
}
