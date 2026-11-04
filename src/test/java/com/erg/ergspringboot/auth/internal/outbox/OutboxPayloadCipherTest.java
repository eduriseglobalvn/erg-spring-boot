package com.erg.ergspringboot.auth.internal.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class OutboxPayloadCipherTest {

    @Test
    void prodProfileRefusesMissingOrBuiltInKey() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new OutboxPayloadCipher("", prod)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new OutboxPayloadCipher(OutboxPayloadCipher.DEVELOPMENT_KEY, prod))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prodProfileAcceptsConfiguredKey() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        OutboxPayloadCipher cipher = new OutboxPayloadCipher("operator-provided-outbox-key", prod);

        assertThat(cipher.decrypt(cipher.encrypt("otp-123"))).isEqualTo("otp-123");
    }

    @Test
    void nonProdProfilesKeepDevelopmentFallback() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        OutboxPayloadCipher cipher = new OutboxPayloadCipher("", dev);

        assertThat(new OutboxPayloadCipher(OutboxPayloadCipher.DEVELOPMENT_KEY).decrypt(cipher.encrypt("x")))
                .isEqualTo("x");
    }
}
