package com.erg.ergspringboot.notifications.delivery;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.notifications.NotificationPriority;
import com.erg.ergspringboot.notifications.NotificationTokenCipher;
import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.notifications.config.WebPushVapidKeys;
import com.erg.ergspringboot.notifications.internal.NotificationDeviceMaintenanceService;
import com.erg.ergspringboot.notifications.internal.WebPushSubscriptionValidator;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.notifications.push.SendResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Urgency;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Standard Web Push (VAPID) sender for devices registered with transport WEB_PUSH. The outbox target is the
 * device id; endpoint and keys are read (and decrypted) from the device row at send time and never logged.
 * Requests go through one shared HTTP client with redirects disabled and explicit timeouts; the endpoint
 * host is re-checked against the allowlist before any request is built.
 */
@Component
public class WebPushNotificationDeliveryAdapter implements NotificationDeliveryAdapter, DisposableBean {

    static final NotificationDeliveryKey KEY = NotificationDeliveryKey.of("push", "web_push", "vapid");
    private static final int BODY_MAX = 240;
    private static final int TTL_SECONDS = (int) Duration.ofDays(1).toSeconds();

    static {
        WebPushVapidKeys.ensureProvider();
    }

    /** Executes a prepared push request; the production implementation is the shared async client. */
    interface Transport {
        Future<HttpResponse> execute(HttpPost post);
    }

    private final PushService pushService;
    private final List<String> allowedHostSuffixes;
    private final Transport transport;
    private final CloseableHttpAsyncClient ownedClient;
    private final Duration timeout;
    private final NotificationDeviceRegistrationRepository devices;
    private final NotificationTokenCipher cipher;
    private final NotificationDeviceMaintenanceService maintenance;
    private final ObjectMapper json;

    @Autowired
    public WebPushNotificationDeliveryAdapter(
            WebPushVapidKeys keys,
            NotificationPushProperties pushProperties,
            NotificationDeviceRegistrationRepository devices,
            NotificationTokenCipher cipher,
            NotificationDeviceMaintenanceService maintenance,
            ObjectMapper json
    ) {
        this(keys.pushService(), keys.allowedHostSuffixes(),
                keys.enabled() ? httpClient(pushProperties.getConnectTimeout(), pushProperties.getReadTimeout()) : null,
                pushProperties.getReadTimeout(), devices, cipher, maintenance, json);
    }

    private WebPushNotificationDeliveryAdapter(
            PushService pushService, List<String> allowedHostSuffixes, CloseableHttpAsyncClient client, Duration timeout,
            NotificationDeviceRegistrationRepository devices, NotificationTokenCipher cipher,
            NotificationDeviceMaintenanceService maintenance, ObjectMapper json) {
        this(pushService, allowedHostSuffixes, client == null ? null : post -> client.execute(post, null), client,
                timeout, devices, cipher, maintenance, json);
    }

    /** {@code pushService} null = Web Push disabled (no or invalid VAPID keys); every delivery fails permanently. */
    WebPushNotificationDeliveryAdapter(
            PushService pushService,
            List<String> allowedHostSuffixes,
            Transport transport,
            CloseableHttpAsyncClient ownedClient,
            Duration timeout,
            NotificationDeviceRegistrationRepository devices,
            NotificationTokenCipher cipher,
            NotificationDeviceMaintenanceService maintenance,
            ObjectMapper json
    ) {
        this.pushService = pushService;
        this.allowedHostSuffixes = allowedHostSuffixes == null ? List.of() : List.copyOf(allowedHostSuffixes);
        this.transport = transport;
        this.ownedClient = ownedClient;
        this.timeout = positive(timeout, Duration.ofSeconds(10));
        this.devices = devices;
        this.cipher = cipher;
        this.maintenance = maintenance;
        this.json = json;
    }

    /** One long-lived client: no redirects (a 3xx is never followed), bounded connect/socket/lease waits. */
    static CloseableHttpAsyncClient httpClient(Duration connectTimeout, Duration readTimeout) {
        int connect = (int) positive(connectTimeout, Duration.ofSeconds(3)).toMillis();
        int read = (int) positive(readTimeout, Duration.ofSeconds(10)).toMillis();
        CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setRedirectsEnabled(false)
                        .setRelativeRedirectsAllowed(false)
                        .setCircularRedirectsAllowed(false)
                        .setConnectTimeout(connect)
                        .setSocketTimeout(read)
                        .setConnectionRequestTimeout(connect)
                        .build())
                .disableCookieManagement()
                .build();
        client.start();
        return client;
    }

    @Override
    public void destroy() throws IOException {
        if (ownedClient != null) ownedClient.close();
    }

    @Override
    public Set<NotificationDeliveryKey> keys() {
        return Set.of(KEY);
    }

    @Override
    public SendResult deliver(NotificationDeliveryRequest request) {
        if (pushService == null || transport == null) {
            return permanent("WEB_PUSH_NOT_CONFIGURED", "VAPID keys are not configured or invalid.", 0, 0);
        }
        NotificationDeviceRegistrationEntity device = devices.findById(request.targetRef())
                .filter(found -> request.tenantId().equals(found.getTenantId()))
                .filter(found -> Boolean.TRUE.equals(found.getEnabled()) && "WEB_PUSH".equals(found.getTransport()))
                .orElse(null);
        if (device == null) {
            return permanent("WEB_PUSH_DEVICE_UNAVAILABLE", "Device is gone or disabled.", 0, 0);
        }
        long started = System.nanoTime();
        HttpPost post;
        try {
            String endpoint = WebPushSubscriptionValidator.endpoint(cipher.decrypt(device.getRegistrationToken()),
                    allowedHostSuffixes);
            NotificationPriority priority = NotificationPriority.fromStored(request.payload().data().get("priority"));
            Notification notification = Notification.builder()
                    .endpoint(endpoint)
                    .userPublicKey(cipher.decrypt(device.getP256dh()))
                    .userAuth(cipher.decrypt(device.getAuth()))
                    .payload(payload(request, priority))
                    .ttl(TTL_SECONDS)
                    .urgency(priority == NotificationPriority.URGENT ? Urgency.HIGH : Urgency.NORMAL)
                    .build();
            post = pushService.preparePost(notification, Encoding.AES128GCM);
        } catch (BadRequestException exception) {
            // Stored endpoint is not (or no longer) on the allowlist: never contact it.
            return permanent("WEB_PUSH_ENDPOINT_NOT_ALLOWED", "Endpoint host is not an allowed push service.", 0, started);
        } catch (Exception exception) {
            // Undecryptable or malformed stored subscription: retrying cannot fix it.
            return permanent("WEB_PUSH_SUBSCRIPTION_INVALID", exception.getClass().getSimpleName(), 0, started);
        }
        Future<HttpResponse> future = null;
        try {
            future = transport.execute(post);
            return classify(request, device, future.get(timeout.toMillis(), TimeUnit.MILLISECONDS), started);
        } catch (TimeoutException exception) {
            future.cancel(true);
            return SendResult.failure(true, false, 0, "WEB_PUSH_TIMEOUT", "Push service did not answer in time.",
                    null, elapsed(started));
        } catch (InterruptedException exception) {
            if (future != null) future.cancel(true);
            Thread.currentThread().interrupt();
            return SendResult.failure(true, false, 0, "WEB_PUSH_INTERRUPTED", "Interrupted.", null, elapsed(started));
        } catch (Exception exception) {
            // Transport faults follow the outbox retry/backoff contract; the message never carries the endpoint.
            return SendResult.failure(true, false, 0, "WEB_PUSH_TRANSPORT_ERROR",
                    exception.getClass().getSimpleName(), null, elapsed(started));
        }
    }

    private SendResult classify(NotificationDeliveryRequest request, NotificationDeviceRegistrationEntity device,
                                HttpResponse response, long started) {
        int status = response.getStatusLine().getStatusCode();
        if (status >= 200 && status < 300) {
            return SendResult.success(status, null, elapsed(started));
        }
        if (status == 404 || status == 410) {
            maintenance.expireDevice(request.tenantId(), device.getId());
            return SendResult.failure(false, true, status, "WEB_PUSH_SUBSCRIPTION_GONE",
                    "Push service reported the subscription gone.", null, elapsed(started));
        }
        if (status == 429 || status >= 500) {
            return SendResult.failure(true, false, status, "WEB_PUSH_HTTP_" + status,
                    "Push service is busy or failing.", retryAfter(response), elapsed(started));
        }
        if (status >= 300 && status < 400) {
            return permanent("WEB_PUSH_REDIRECT_REFUSED", "Push service answered with a redirect.", status, started);
        }
        // 400/401/403/413 and other client errors: the same request can never succeed.
        return permanent("WEB_PUSH_HTTP_" + status, "Push service rejected the message.", status, started);
    }

    private byte[] payload(NotificationDeliveryRequest request, NotificationPriority priority) {
        String body = request.payload().body() == null ? "" : request.payload().body();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationId", request.notificationId());
        payload.put("title", request.payload().title());
        payload.put("body", body.length() > BODY_MAX ? body.substring(0, BODY_MAX) + "…" : body);
        payload.put("priority", priority.name());
        payload.put("url", "/notifications/" + request.notificationId());
        payload.put("tag", request.payload().collapseKey() == null ? request.notificationId() : request.payload().collapseKey());
        payload.put("requireInteraction", priority == NotificationPriority.URGENT);
        return json.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
    }

    private static Duration retryAfter(HttpResponse response) {
        Header header = response.getFirstHeader("Retry-After");
        if (header == null) return null;
        try {
            long seconds = Long.parseLong(header.getValue().trim());
            return seconds > 0 ? Duration.ofSeconds(Math.min(seconds, Duration.ofHours(1).toSeconds())) : null;
        } catch (NumberFormatException exception) {
            return null; // HTTP-date form: fall back to the worker's own backoff.
        }
    }

    private static SendResult permanent(String code, String message, int status, long started) {
        return SendResult.failure(false, false, status, code, message, null, started == 0 ? 0 : elapsed(started));
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }

    private static long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
