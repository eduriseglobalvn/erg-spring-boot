package com.erg.ergspringboot.notifications.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.notifications.NotificationTokenCipher;
import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.notifications.internal.NotificationDeviceMaintenanceService;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.notifications.push.SendResult;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Urgency;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class WebPushNotificationDeliveryAdapterTest {

    private static final String TENANT = "tenant-wp";
    private static final String DEVICE_ID = "device-1";
    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc123";

    private final ObjectMapper json = JsonMapper.builder().build();
    private final NotificationDeviceRegistrationRepository devices = mock(NotificationDeviceRegistrationRepository.class);
    private final NotificationDeviceMaintenanceService maintenance = mock(NotificationDeviceMaintenanceService.class);
    private final PushService pushService = mock(PushService.class);
    private NotificationTokenCipher cipher;

    @BeforeEach
    void setUp() throws Exception {
        NotificationPushProperties properties = new NotificationPushProperties();
        properties.getCredentials().setRegistrationTokenEncryptionKey("unit-test-key");
        cipher = new NotificationTokenCipher(properties);
        when(devices.findById(DEVICE_ID)).thenReturn(Optional.of(device()));
    }

    @Test
    void sendsOnceWithTheInboxUrlTitleAndPriority() throws Exception {
        when(pushService.sendAsync(any(Notification.class))).thenReturn(CompletableFuture.completedFuture(response(201)));

        SendResult result = adapter().deliver(request(Map.of("priority", "IMPORTANT", "campaignId", "c-1")));

        assertThat(result.success()).isTrue();
        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(pushService, times(1)).sendAsync(sent.capture());
        assertThat(sent.getValue().getEndpoint()).isEqualTo(ENDPOINT);
        JsonNode payload = json.readTree(new String(sent.getValue().getPayload(), StandardCharsets.UTF_8));
        assertThat(payload.get("url").asText()).isEqualTo("/notifications/msg-1");
        assertThat(payload.get("title").asText()).isEqualTo("Họp phụ huynh");
        assertThat(payload.get("body").asText()).isEqualTo("Tối thứ 6");
        assertThat(payload.get("priority").asText()).isEqualTo("IMPORTANT");
        assertThat(payload.get("tag").asText()).isEqualTo("msg-1");
        assertThat(payload.get("requireInteraction").asBoolean()).isFalse();
        assertThat(sent.getValue().getUrgency()).isEqualTo(Urgency.NORMAL);
    }

    @Test
    void urgentRequiresInteractionAndHighUrgency() throws Exception {
        when(pushService.sendAsync(any(Notification.class))).thenReturn(CompletableFuture.completedFuture(response(201)));

        adapter().deliver(request(Map.of("priority", "URGENT")));

        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(pushService).sendAsync(sent.capture());
        JsonNode payload = json.readTree(new String(sent.getValue().getPayload(), StandardCharsets.UTF_8));
        assertThat(payload.get("requireInteraction").asBoolean()).isTrue();
        assertThat(sent.getValue().getUrgency()).isEqualTo(Urgency.HIGH);
    }

    @Test
    void goneSubscriptionExpiresTheDeviceThroughMaintenance() throws Exception {
        when(pushService.sendAsync(any(Notification.class))).thenReturn(CompletableFuture.completedFuture(response(410)));

        SendResult result = adapter().deliver(request(Map.of()));

        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.invalidTarget()).isTrue();
        verify(maintenance).expireDevice(TENANT, DEVICE_ID);
    }

    @Test
    void notFoundSubscriptionAlsoExpiresTheDevice() throws Exception {
        when(pushService.sendAsync(any(Notification.class))).thenReturn(CompletableFuture.completedFuture(response(404)));

        adapter().deliver(request(Map.of()));

        verify(maintenance).expireDevice(TENANT, DEVICE_ID);
    }

    @Test
    void serverErrorIsRetryableAndKeepsTheDevice() throws Exception {
        when(pushService.sendAsync(any(Notification.class))).thenReturn(CompletableFuture.completedFuture(response(503)));

        SendResult result = adapter().deliver(request(Map.of()));

        assertThat(result.retryable()).isTrue();
        assertThat(result.invalidTarget()).isFalse();
        verify(maintenance, never()).expireDevice(any(), any());
    }

    @Test
    void transportFailureIsRetryable() throws Exception {
        when(pushService.sendAsync(any(Notification.class)))
                .thenReturn(CompletableFuture.failedFuture(new java.io.IOException("connection reset")));

        SendResult result = adapter().deliver(request(Map.of()));

        assertThat(result.retryable()).isTrue();
        assertThat(result.errorMessage()).doesNotContain(ENDPOINT);
    }

    @Test
    void withoutVapidKeysNothingIsSent() {
        WebPushNotificationDeliveryAdapter disabled = new WebPushNotificationDeliveryAdapter(
                null, Duration.ofSeconds(5), devices, cipher, maintenance, json);

        SendResult result = disabled.deliver(request(Map.of()));

        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.errorCode()).isEqualTo("WEB_PUSH_NOT_CONFIGURED");
        verifyNoInteractions(pushService);
    }

    @Test
    void deviceOfAnotherTenantIsNeverUsed() throws Exception {
        NotificationDeviceRegistrationEntity foreign = device();
        foreign.setTenantId("other");
        when(devices.findById(DEVICE_ID)).thenReturn(Optional.of(foreign));

        SendResult result = adapter().deliver(request(Map.of()));

        assertThat(result.success()).isFalse();
        verifyNoInteractions(pushService);
    }

    @Test
    void handlesTheWebPushKey() {
        assertThat(adapter().keys()).containsExactly(NotificationDeliveryKey.of("push", "web_push", "vapid"));
    }

    private WebPushNotificationDeliveryAdapter adapter() {
        return new WebPushNotificationDeliveryAdapter(pushService, Duration.ofSeconds(5), devices, cipher, maintenance, json);
    }

    private NotificationDeliveryRequest request(Map<String, String> data) {
        Map<String, String> payload = new java.util.HashMap<>(data);
        payload.put("deviceRegistrationId", DEVICE_ID);
        return new NotificationDeliveryRequest(TENANT, "msg-1", "push", "web_push", "vapid", DEVICE_ID,
                new NotificationDeliveryPayload("Họp phụ huynh", "Tối thứ 6", null, null, payload));
    }

    private NotificationDeviceRegistrationEntity device() throws Exception {
        NotificationDeviceRegistrationEntity device = new NotificationDeviceRegistrationEntity();
        device.setId(DEVICE_ID);
        device.setTenantId(TENANT);
        device.setEnabled(true);
        device.setTransport("WEB_PUSH");
        device.setRegistrationToken(cipher.encrypt(ENDPOINT));
        device.setP256dh(cipher.encrypt(clientPublicKey()));
        device.setAuth(cipher.encrypt(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16])));
        return device;
    }

    private static String clientPublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        ECPublicKey key = (ECPublicKey) pair.getPublic();
        byte[] x = unsigned(key.getW().getAffineX().toByteArray());
        byte[] y = unsigned(key.getW().getAffineY().toByteArray());
        byte[] raw = new byte[65];
        raw[0] = 4;
        System.arraycopy(x, 0, raw, 33 - x.length, x.length);
        System.arraycopy(y, 0, raw, 65 - y.length, y.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static byte[] unsigned(byte[] value) {
        return value.length > 32 ? java.util.Arrays.copyOfRange(value, value.length - 32, value.length) : value;
    }

    private static HttpResponse response(int status) {
        return new BasicHttpResponse(HttpVersion.HTTP_1_1, status, "status");
    }
}
