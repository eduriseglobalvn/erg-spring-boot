package com.erg.ergspringboot.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.notifications.NotificationRequests.RegisterDeviceRequest;
import com.erg.ergspringboot.common.request.notifications.NotificationRequests.WebPushKeysRequest;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.notifications.NotificationTokenCipher;
import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.notifications.config.WebPushProperties;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationTopicMembershipRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class NotificationDeviceServiceWebPushTest {

    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc";
    private static final String P256DH = Base64.getUrlEncoder().withoutPadding().encodeToString(filled(65));
    private static final String AUTH = Base64.getUrlEncoder().withoutPadding().encodeToString(filled(16));

    private final NotificationDeviceRegistrationRepository devices = mock(NotificationDeviceRegistrationRepository.class);
    private final NotificationTopicMembershipRepository topics = mock(NotificationTopicMembershipRepository.class);
    private final WebPushProperties webPush = new WebPushProperties();
    private NotificationTokenCipher cipher;
    private NotificationDeviceService service;

    @BeforeEach
    void setUp() {
        NotificationPushProperties properties = new NotificationPushProperties();
        properties.getCredentials().setRegistrationTokenEncryptionKey("unit-test-key");
        cipher = new NotificationTokenCipher(properties);
        webPush.setPublicKey("public");
        webPush.setPrivateKey("private");
        webPush.setSubject("mailto:ops@example.test");
        service = new NotificationDeviceService(devices, topics, cipher, webPush);
        when(devices.findByTenantIdAndRegistrationTokenHash(anyString(), anyString())).thenReturn(Optional.empty());
        when(devices.save(any())).thenAnswer(invocation -> {
            NotificationDeviceRegistrationEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) entity.setId("device-1");
            return entity;
        });
        when(topics.findByDeviceRegistrationIdAndTopicName(anyString(), anyString())).thenReturn(Optional.empty());
    }

    @Test
    void registersAPushSubscriptionEncryptedAndDedupedByEndpointHash() throws Exception {
        var response = service.register("tenant", "user", "elearning", request("web_push", ENDPOINT, P256DH, AUTH));

        assertThat(response.transport()).isEqualTo("WEB_PUSH");
        verify(devices).findByTenantIdAndRegistrationTokenHash("tenant", sha256(ENDPOINT));
        ArgumentCaptor<NotificationDeviceRegistrationEntity> saved = ArgumentCaptor.forClass(NotificationDeviceRegistrationEntity.class);
        verify(devices).save(saved.capture());
        NotificationDeviceRegistrationEntity device = saved.getValue();
        assertThat(device.getTransport()).isEqualTo("WEB_PUSH");
        assertThat(device.getRegistrationTokenHash()).isEqualTo(sha256(ENDPOINT));
        // Stored ciphertext only; the cipher round-trips each secret.
        assertThat(device.getRegistrationToken()).startsWith("enc:v1:").doesNotContain("fcm.googleapis");
        assertThat(device.getP256dh()).startsWith("enc:v1:").isNotEqualTo(P256DH);
        assertThat(device.getAuth()).startsWith("enc:v1:").isNotEqualTo(AUTH);
        assertThat(cipher.decrypt(device.getRegistrationToken())).isEqualTo(ENDPOINT);
        assertThat(cipher.decrypt(device.getP256dh())).isEqualTo(P256DH);
        assertThat(cipher.decrypt(device.getAuth())).isEqualTo(AUTH);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://fcm.googleapis.com/fcm/send/abc",
            "https://localhost/push",
            "https://push.localhost/x",
            "https://127.0.0.1/push",
            "https://10.0.0.5/push",
            "https://192.168.1.10:8443/push",
            "https://169.254.169.254/latest",
            "https://[::1]/push",
            "https://[fd00::1]/push",
            "https://2130706433/push",
            "https://user:pass@fcm.googleapis.com/x",
            "ftp://fcm.googleapis.com/x",
            "not a url",
            ""
    })
    void rejectsEndpointsThatAreNotPublicHttps(String endpoint) {
        assertThatThrownBy(() -> service.register("tenant", "user", "lms", request("WEB_PUSH", endpoint, P256DH, AUTH)))
                .isInstanceOf(BadRequestException.class);
        verify(devices, never()).save(any());
    }

    @Test
    void rejectsOverlongEndpointAndMalformedKeys() {
        String longEndpoint = "https://fcm.googleapis.com/" + "a".repeat(2050);
        assertThatThrownBy(() -> service.register("t", "u", "lms", request("WEB_PUSH", longEndpoint, P256DH, AUTH)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.register("t", "u", "lms", request("WEB_PUSH", ENDPOINT, AUTH, AUTH)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("p256dh");
        assertThatThrownBy(() -> service.register("t", "u", "lms", request("WEB_PUSH", ENDPOINT, P256DH, "!!")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("auth");
        assertThatThrownBy(() -> service.register("t", "u", "lms", request("WEB_PUSH", ENDPOINT, null, null)))
                .isInstanceOf(BadRequestException.class);
        verify(devices, never()).save(any());
    }

    @Test
    void webPushRegistrationIsRejectedWhileVapidIsNotConfigured() {
        NotificationDeviceService unconfigured = new NotificationDeviceService(devices, topics, cipher, new WebPushProperties());

        assertThatThrownBy(() -> unconfigured.register("t", "u", "lms", request("WEB_PUSH", ENDPOINT, P256DH, AUTH)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Web Push chưa được cấu hình");
        assertThat(unconfigured.webPushConfig().enabled()).isFalse();
        assertThat(unconfigured.webPushConfig().publicKey()).isNull();
    }

    @Test
    void exposesThePublicKeyOnlyWhenConfigured() {
        assertThat(service.webPushConfig().enabled()).isTrue();
        assertThat(service.webPushConfig().publicKey()).isEqualTo("public");
    }

    @Test
    void unknownTransportIsRejectedAndMissingTransportStaysFcm() {
        assertThatThrownBy(() -> service.register("t", "u", "lms", request("APNS", ENDPOINT, P256DH, AUTH)))
                .isInstanceOf(BadRequestException.class);

        var response = service.register("t", "u", "lms", new RegisterDeviceRequest("lms", "web_pwa", null, "fcm-token",
                ENDPOINT, null, "granted", null, null, null, null, null));

        assertThat(response.transport()).isEqualTo("FCM");
        verify(devices).findByTenantIdAndRegistrationTokenHash(eq("t"), eq(sha256("fcm-token")));
    }

    private static RegisterDeviceRequest request(String transport, String endpoint, String p256dh, String auth) {
        return new RegisterDeviceRequest("lms", "web_pwa", null, endpoint, endpoint, null, "granted", null, null, null,
                transport, new WebPushKeysRequest(p256dh, auth));
    }

    private static byte[] filled(int length) {
        byte[] bytes = new byte[length];
        java.util.Arrays.fill(bytes, (byte) 7);
        return bytes;
    }

    private static String sha256(String value) throws RuntimeException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
