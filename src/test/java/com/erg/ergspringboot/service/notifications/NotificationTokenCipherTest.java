package com.erg.ergspringboot.service.notifications;

import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTokenCipherTest {

    @Test
    void encryptsAndDecryptsRegistrationTokenWhenKeyIsConfigured() {
        NotificationPushProperties properties = new NotificationPushProperties();
        properties.getCredentials().setRegistrationTokenEncryptionKey("0123456789abcdef0123456789abcdef");

        NotificationTokenCipher cipher = new NotificationTokenCipher(properties);
        String encrypted = cipher.encrypt("raw-registration-token");

        assertThat(encrypted)
                .startsWith("enc:v1:")
                .doesNotContain("raw-registration-token");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("raw-registration-token");
    }

    @Test
    void rejectsPlaintextTokenWhenEncryptionIsRequired() {
        NotificationPushProperties properties = new NotificationPushProperties();
        properties.getCredentials().setRegistrationTokenEncryptionKey("0123456789abcdef0123456789abcdef");
        properties.getCredentials().setRegistrationTokenEncryptionRequired(true);

        NotificationTokenCipher cipher = new NotificationTokenCipher(properties);

        assertThatThrownBy(() -> cipher.decrypt("raw-registration-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not encrypted");
    }

    @Test
    void failsFastWhenEncryptionIsRequiredWithoutKey() {
        NotificationPushProperties properties = new NotificationPushProperties();
        properties.getCredentials().setRegistrationTokenEncryptionRequired(true);

        assertThatThrownBy(() -> new NotificationTokenCipher(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required");
    }
}
