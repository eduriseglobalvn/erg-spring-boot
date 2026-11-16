package com.erg.ergspringboot.auth.internal.login;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionIssuerPolicyTest {

    @Test
    void pdtGetsAdminSessionPolicy() {
        // the PDT role has no "admin" in its name; the admin workspace permission is what counts
        Duration pdt = SessionIssuer.sessionTtl(List.of("admin.workspace.access", "lms.class.manage"));

        assertThat(pdt).isEqualTo(Duration.ofHours(8));
        assertThat(SessionIssuer.sessionTtl(List.of("*"))).isEqualTo(pdt);
        assertThat(SessionIssuer.sessionTtl(List.of("teacher.workspace.access"))).isEqualTo(Duration.ofHours(12));
    }
}
