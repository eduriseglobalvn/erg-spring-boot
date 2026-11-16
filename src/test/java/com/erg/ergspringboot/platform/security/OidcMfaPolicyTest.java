package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OidcMfaPolicyTest {
    private final OidcMfaPolicy policy = new OidcMfaPolicy();

    @Test
    void adminWorkspaceRequiresMfaEvidence() {
        assertThat(policy.satisfied(Set.of("admin.workspace.access"), Map.of("amr", List.of("pwd")))).isFalse();
        assertThat(policy.satisfied(Set.of("*"), Map.of("amr", List.of("pwd")))).isFalse();
        assertThat(policy.satisfied(Set.of("admin.workspace.access"), Map.of("amr", List.of("pwd", "otp")))).isTrue();
        assertThat(policy.satisfied(Set.of("admin.workspace.access"), Map.of("amr", List.of("webauthn")))).isTrue();
    }

    @Test
    void otherWorkspacesDoNotRequireStepUpAtLogin() {
        assertThat(policy.satisfied(Set.of("teacher.workspace.access"), Map.of("amr", List.of("pwd")))).isTrue();
        assertThat(policy.satisfied(Set.of("student.workspace.access"), Map.of("amr", List.of("pwd")))).isTrue();
    }
}
