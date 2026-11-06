package com.erg.ergspringboot.notifications.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.notifications.NotificationTokenCipher;
import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.notifications.config.WebPushVapidKeys;
import com.erg.ergspringboot.notifications.internal.NotificationDeviceMaintenanceService;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.notifications.push.SendResult;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Urgency;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.message.BasicHttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class WebPushNotificationDeliveryAdapterTest {

    private static final String TENANT = "tenant-wp";
    private static final String DEVICE_ID = "device-1";
    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc123";
    private static final List<String> ALLOWED =
            List.of("fcm.googleapis.com", "push.services.mozilla.com", "notify.windows.com", "push.apple.com");

    private final ObjectMapper json = JsonMapper.builder().build();
    private final NotificationDeviceRegistrationRepository devices = mock(NotificationDeviceRegistrationRepository.class);
    private final NotificationDeviceMaintenanceService maintenance = mock(NotificationDeviceMaintenanceService.class);
    private final PushService pushService = mock(PushService.class);
    private final List<HttpPost> executed = new ArrayList<>();
    private Future<HttpResponse> next;
    private NotificationTokenCipher cipher;

    @BeforeEach
    void setUp() throws Exception {
        WebPushVapidKeys.ensureProvider();
        NotificationPushProperties properties = new NotificationPushProperties();
        properties.getCredentials().setRegistrationTokenEncryptionKey("unit-test-key");
        cipher = new NotificationTokenCipher(properties);
        when(devices.findById(DEVICE_ID)).thenReturn(Optional.of(device(ENDPOINT)));
        when(pushService.preparePost(any(Notification.class), eq(Encoding.AES128GCM)))
                .thenAnswer(invocation -> new HttpPost(((Notification) invocation.getArgument(0)).getEndpoint()));
    }

    @Test
    void sendsOnceWithTheInboxUrlTitleAndPriority() throws Exception {
        respond(201);

        SendResult result = adapter().deliver(request(Map.of("priority", "IMPORTANT", "campaignId", "c-1")));

        assertThat(result.success()).isTrue();
        assertThat(executed).singleElement().satisfies(post -> assertThat(post.getURI().toString()).isEqualTo(ENDPOINT));
        Notification sent = prepared();
        JsonNode payload = json.readTree(new String(sent.getPayload(), StandardCharsets.UTF_8));
        assertThat(payload.get("url").asText()).isEqualTo("/notifications/msg-1");
        assertThat(payload.get("title").asText()).isEqualTo("Họp phụ huynh");
        assertThat(payload.get("body").asText()).isEqualTo("Tối thứ 6");
        assertThat(payload.get("priority").asText()).isEqualTo("IMPORTANT");
        assertThat(payload.get("tag").asText()).isEqualTo("msg-1");
        assertThat(payload.get("requireInteraction").asBoolean()).isFalse();
        assertThat(sent.getUrgency()).isEqualTo(Urgency.NORMAL);
    }

    @Test
    void urgentRequiresInteractionAndHighUrgency() throws Exception {
        respond(201);

        adapter().deliver(request(Map.of("priority", "URGENT")));

        Notification sent = prepared();
        JsonNode payload = json.readTree(new String(sent.getPayload(), StandardCharsets.UTF_8));
        assertThat(payload.get("requireInteraction").asBoolean()).isTrue();
        assertThat(sent.getUrgency()).isEqualTo(Urgency.HIGH);
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 410})
    void goneSubscriptionExpiresTheDeviceThroughMaintenance(int status) {
        respond(status);

        SendResult result = adapter().deliver(request(Map.of()));

        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.invalidTarget()).isTrue();
        verify(maintenance).expireDevice(TENANT, DEVICE_ID);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 413})
    void clientErrorsArePermanentAndKeepTheDevice(int status) {
        respond(status);

        SendResult result = adapter().deliver(request(Map.of()));

        assertThat(result.retryable()).isFalse();
        assertThat(result.invalidTarget()).isFalse();
        assertThat(result.errorCode()).isEqualTo("WEB_PUSH_HTTP_" + status);
        verify(maintenance, never()).expireDevice(any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 503})
    void busyOrFailingServiceIsRetryableHonouringRetryAfter(int status) {
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, status, "status");
        response.addHeader("Retry-After", "120");
        next = CompletableFuture.completedFuture(response);

        SendResult result = adapter().deliver(request(Map.of()));

        assertThat(result.retryable()).isTrue();
        assertThat(result.invalidTarget()).isFalse();
        assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(120));
        verify(maintenance, never()).expireDevice(any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 307, 308})
    void redirectIsAPermanentFailureAndIsNotFollowed(int status) {
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, status, "moved");
        response.addHeader("Location", "http://169.254.169.254/latest/meta-data");
        next = CompletableFuture.completedFuture(response);

        SendResult result = adapter().deliver(request(Map.of()));

        assertThat(result.retryable()).isFalse();
        assertThat(result.errorCode()).isEqualTo("WEB_PUSH_REDIRECT_REFUSED");
        assertThat(executed).hasSize(1);
    }

    @Test
    void transportFailureIsRetryableAndNeverEchoesTheEndpoint() {
        next = CompletableFuture.failedFuture(new java.io.IOException("connection reset to " + ENDPOINT));

        SendResult result = adapter().deliver(request(Map.of()));

        assertThat(result.retryable()).isTrue();
        assertThat(result.errorMessage()).doesNotContain(ENDPOINT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void timeoutCancelsTheInFlightRequestAndIsRetryable() throws Exception {
        Future<HttpResponse> hanging = mock(Future.class);
        when(hanging.get(anyLong(), eq(TimeUnit.MILLISECONDS))).thenThrow(new TimeoutException());
        next = hanging;

        SendResult result = adapter().deliver(request(Map.of()));

        assertThat(result.retryable()).isTrue();
        assertThat(result.errorCode()).isEqualTo("WEB_PUSH_TIMEOUT");
        verify(hanging).cancel(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example.com/push",
            "https://evilnotify.windows.com/w",
            "https://169.254.169.254/latest",
            "https://fcm.googleapis.com:8443/fcm/send/x",
            "http://fcm.googleapis.com/fcm/send/x"
    })
    void storedEndpointOutsideTheAllowlistIsNeverContacted(String endpoint) throws Exception {
        when(devices.findById(DEVICE_ID)).thenReturn(Optional.of(device(endpoint)));

        SendResult result = adapter().deliver(request(Map.of()));

        assertThat(result.retryable()).isFalse();
        assertThat(result.errorCode()).isEqualTo("WEB_PUSH_ENDPOINT_NOT_ALLOWED");
        assertThat(executed).isEmpty();
        verify(pushService, never()).preparePost(any(), any());
    }

    @Test
    void withoutVapidKeysNothingIsSent() {
        WebPushNotificationDeliveryAdapter disabled = new WebPushNotificationDeliveryAdapter(
                null, ALLOWED, null, null, Duration.ofSeconds(5), devices, cipher, maintenance, json);

        SendResult result = disabled.deliver(request(Map.of()));

        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.errorCode()).isEqualTo("WEB_PUSH_NOT_CONFIGURED");
        verifyNoInteractions(pushService);
    }

    @Test
    void deviceOfAnotherTenantIsNeverUsed() throws Exception {
        NotificationDeviceRegistrationEntity foreign = device(ENDPOINT);
        foreign.setTenantId("other");
        when(devices.findById(DEVICE_ID)).thenReturn(Optional.of(foreign));

        SendResult result = adapter().deliver(request(Map.of()));

        assertThat(result.success()).isFalse();
        assertThat(executed).isEmpty();
    }

    @Test
    void handlesTheWebPushKey() {
        assertThat(adapter().keys()).containsExactly(NotificationDeliveryKey.of("push", "web_push", "vapid"));
    }

    /** The real shared client: a 307 from the push service is returned as-is, the Location is never requested. */
    @Test
    void sharedHttpClientDoesNotFollowRedirects() throws Exception {
        AtomicInteger redirectTargetHits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/push", exchange -> {
            exchange.getResponseHeaders().add("Location", "/internal");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        server.createContext("/internal", exchange -> {
            redirectTargetHits.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try (CloseableHttpAsyncClient client = WebPushNotificationDeliveryAdapter.httpClient(
                Duration.ofSeconds(2), Duration.ofSeconds(2))) {
            HttpResponse response = client.execute(
                    new HttpPost("http://127.0.0.1:" + server.getAddress().getPort() + "/push"), null)
                    .get(5, TimeUnit.SECONDS);

            assertThat(response.getStatusLine().getStatusCode()).isEqualTo(307);
            assertThat(redirectTargetHits).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    private WebPushNotificationDeliveryAdapter adapter() {
        WebPushNotificationDeliveryAdapter.Transport transport = post -> {
            executed.add(post);
            return next;
        };
        return new WebPushNotificationDeliveryAdapter(pushService, ALLOWED, transport, null, Duration.ofSeconds(5),
                devices, cipher, maintenance, json);
    }

    private void respond(int status) {
        next = CompletableFuture.completedFuture(new BasicHttpResponse(HttpVersion.HTTP_1_1, status, "status"));
    }

    private Notification prepared() throws Exception {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(pushService, times(1)).preparePost(captor.capture(), eq(Encoding.AES128GCM));
        return captor.getValue();
    }

    private NotificationDeliveryRequest request(Map<String, String> data) {
        Map<String, String> payload = new java.util.HashMap<>(data);
        payload.put("deviceRegistrationId", DEVICE_ID);
        return new NotificationDeliveryRequest(TENANT, "msg-1", "push", "web_push", "vapid", DEVICE_ID,
                new NotificationDeliveryPayload("Họp phụ huynh", "Tối thứ 6", null, null, payload));
    }

    private NotificationDeviceRegistrationEntity device(String endpoint) throws Exception {
        NotificationDeviceRegistrationEntity device = new NotificationDeviceRegistrationEntity();
        device.setId(DEVICE_ID);
        device.setTenantId(TENANT);
        device.setEnabled(true);
        device.setTransport("WEB_PUSH");
        device.setRegistrationToken(cipher.encrypt(endpoint));
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
}
