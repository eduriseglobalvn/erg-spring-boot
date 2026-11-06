package com.erg.ergspringboot.notifications.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class WebPushVapidKeysTest {

    @Test
    void validKeyPairEnablesWebPush() {
        WebPushProperties properties = VapidTestKeys.properties();

        WebPushVapidKeys keys = new WebPushVapidKeys(properties);

        assertThat(keys.enabled()).isTrue();
        assertThat(keys.pushService()).isNotNull();
        assertThat(keys.publicKey()).isEqualTo(properties.getPublicKey());
    }

    @Test
    void missingKeysDisableWithoutError() {
        WebPushVapidKeys keys = new WebPushVapidKeys(new WebPushProperties());

        assertThat(keys.enabled()).isFalse();
        assertThat(keys.publicKey()).isNull();
    }

    @Test
    void malformedKeysDoNotAbortBootAndAreNotLogged(CapturedOutput output) {
        WebPushProperties properties = new WebPushProperties();
        properties.setPublicKey("not-a-key-SECRETPUB");
        properties.setPrivateKey("also-not-a-key-SECRETPRIV");
        properties.setSubject("mailto:ops@example.test");

        WebPushVapidKeys keys = new WebPushVapidKeys(properties);

        assertThat(keys.enabled()).isFalse();
        assertThat(keys.publicKey()).isNull();
        assertThat(output.getAll()).contains("Web Push disabled").doesNotContain("SECRETPUB").doesNotContain("SECRETPRIV");
    }

    @Test
    void mismatchedKeyPairIsDisabled(CapturedOutput output) {
        WebPushProperties properties = VapidTestKeys.properties();
        String otherPrivate = VapidTestKeys.generate()[1];
        properties.setPrivateKey(otherPrivate);

        WebPushVapidKeys keys = new WebPushVapidKeys(properties);

        assertThat(keys.enabled()).isFalse();
        assertThat(output.getAll()).contains("does not match").doesNotContain(otherPrivate);
    }

    @Test
    void subjectMustBeMailtoOrHttps() {
        WebPushProperties properties = VapidTestKeys.properties();
        properties.setSubject("ops@example.test");

        assertThat(new WebPushVapidKeys(properties).enabled()).isFalse();
    }
}
