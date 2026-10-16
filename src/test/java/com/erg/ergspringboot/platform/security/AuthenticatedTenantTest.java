package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuthenticatedTenantTest {

    @Test
    void normalizesVerifiedTenantIdentity() {
        assertThat(AuthenticatedTenant.require(" tenant-a ")).isEqualTo("tenant-a");
    }

    @Test
    void rejectsMissingTenantInsteadOfFallingBackToTheDefaultTenant() {
        assertThatThrownBy(() -> AuthenticatedTenant.require("  "))
                .isInstanceOf(AuthenticationRequiredException.class);
    }
}
