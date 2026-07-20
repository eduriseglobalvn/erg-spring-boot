package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OidcMfaPolicyTest {
    private final OidcMfaPolicy policy = new OidcMfaPolicy();

    @Test
    void privilegedRolesRequireMfaEvidence() {
        assertThat(policy.satisfied(List.of("teacher"), Map.of("amr", List.of("pwd")))).isFalse();
        assertThat(policy.satisfied(List.of("system.super_admin"), Map.of("amr", List.of("pwd", "otp")))).isTrue();
        assertThat(policy.satisfied(List.of("school.manager"), Map.of("amr", List.of("webauthn")))).isTrue();
    }

    @Test
    void ordinaryStudentDoesNotRequireStepUpAtLogin() {
        assertThat(policy.satisfied(List.of("student"), Map.of("amr", List.of("pwd")))).isTrue();
    }
}
