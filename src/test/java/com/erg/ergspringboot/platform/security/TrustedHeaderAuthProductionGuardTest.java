package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class TrustedHeaderAuthProductionGuardTest {

    @Test
    void stagingWithTrustedHeaderAuthFailsStartup() {
        assertThatThrownBy(() -> guard(true, "staging").rejectTrustedHeaderAuthOutsideTests())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prodWithTrustedHeaderAuthFailsStartup() {
        assertThatThrownBy(() -> guard(true, "prod").rejectTrustedHeaderAuthOutsideTests())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void noActiveProfileWithTrustedHeaderAuthFailsStartup() {
        assertThatThrownBy(() -> guard(true).rejectTrustedHeaderAuthOutsideTests())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testProfileMayEnableTrustedHeaderAuth() {
        assertThatCode(() -> guard(true, "test", "prototype").rejectTrustedHeaderAuthOutsideTests())
                .doesNotThrowAnyException();
    }

    @Test
    void disabledFlagBootsInAnyProfile() {
        assertThatCode(() -> guard(false, "prod").rejectTrustedHeaderAuthOutsideTests())
                .doesNotThrowAnyException();
    }

    private static TrustedHeaderAuthProductionGuard guard(boolean enabled, String... profiles) {
        ErgSecurityProperties properties = new ErgSecurityProperties();
        properties.setTrustedHeaderAuthEnabled(enabled);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new TrustedHeaderAuthProductionGuard(properties, environment);
    }
}
