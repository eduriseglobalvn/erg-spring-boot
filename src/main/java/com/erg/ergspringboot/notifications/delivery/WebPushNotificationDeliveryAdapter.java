package com.erg.ergspringboot.notifications.delivery;

import com.erg.ergspringboot.notifications.NotificationPriority;
import com.erg.ergspringboot.notifications.NotificationTokenCipher;
import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.notifications.config.WebPushProperties;
import com.erg.ergspringboot.notifications.internal.NotificationDeviceMaintenanceService;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.notifications.push.SendResult;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Urgency;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Standard Web Push (VAPID) sender for devices registered with transport WEB_PUSH. The outbox target is the
 * device id; endpoint and keys are read (and decrypted) from the device row at send time, never logged.
 */
@Component
public class WebPushNotificationDeliveryAdapter implements NotificationDeliveryAdapter {

    static final NotificationDeliveryKey KEY = NotificationDeliveryKey.of("push", "web_push", "vapid");
    private static final int BODY_MAX = 240;
    private static final int TTL_SECONDS = (int) Duration.ofDays(1).toSeconds();

    static {
        // web-push loads EC keys through the "BC" provider.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final PushService pushService;
    private final Duration timeout;
    private final NotificationDeviceRegistrationRepository devices;
    private final NotificationTokenCipher cipher;
    private final NotificationDeviceMaintenanceService maintenance;
    private final ObjectMapper json;

    @Autowired
    public WebPushNotificationDeliveryAdapter(
            WebPushProperties properties,
            NotificationPushProperties pushProperties,
            NotificationDeviceRegistrationRepository devices,
            NotificationTokenCipher cipher,
            NotificationDeviceMaintenanceService maintenance,
            ObjectMapper json
    ) {
        this(pushService(properties), pushProperties.getReadTimeout(), devices, cipher, maintenance, json);
    }

    /** {@code pushService} null = Web Push not configured; every delivery fails permanently. */
    WebPushNotificationDeliveryAdapter(
            PushService pushService,
            Duration timeout,
            NotificationDeviceRegistrationRepository devices,
            NotificationTokenCipher cipher,
            NotificationDeviceMaintenanceService maintenance,
            ObjectMapper json
    ) {
        this.pushService = pushService;
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(10) : timeout;
        this.devices = devices;
        this.cipher = cipher;
        this.maintenance = maintenance;
        this.json = json;
    }

    @Override
    public Set<NotificationDeliveryKey> keys() {
        return Set.of(KEY);
    }

    @Override
    public SendResult deliver(NotificationDeliveryRequest request) {
        if (pushService == null) {
            return SendResult.failure(false, false, 0, "WEB_PUSH_NOT_CONFIGURED", "VAPID keys are not configured.", null, 0);
        }
        NotificationDeviceRegistrationEntity device = devices.findById(request.targetRef())
                .filter(found -> request.tenantId().equals(found.getTenantId()))
                .filter(found -> Boolean.TRUE.equals(found.getEnabled()) && "WEB_PUSH".equals(found.getTransport()))
                .orElse(null);
        if (device == null) {
            return SendResult.failure(false, false, 0, "WEB_PUSH_DEVICE_UNAVAILABLE", "Device is gone or disabled.", null, 0);
        }
        long started = System.nanoTime();
        Notification notification;
        try {
            NotificationPriority priority = NotificationPriority.fromStored(request.payload().data().get("priority"));
            notification = Notification.builder()
                    .endpoint(cipher.decrypt(device.getRegistrationToken()))
                    .userPublicKey(cipher.decrypt(device.getP256dh()))
                    .userAuth(cipher.decrypt(device.getAuth()))
                    .payload(payload(request, priority))
                    .ttl(TTL_SECONDS)
                    .urgency(priority == NotificationPriority.URGENT ? Urgency.HIGH : Urgency.NORMAL)
                    .build();
        } catch (GeneralSecurityException | RuntimeException exception) {
            // Undecryptable or malformed stored subscription: retrying cannot fix it.
            return SendResult.failure(false, false, 0, "WEB_PUSH_SUBSCRIPTION_INVALID",
                    exception.getClass().getSimpleName(), null, elapsed(started));
        }
        try {
            HttpResponse response = pushService.sendAsync(notification).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            int status = response.getStatusLine().getStatusCode();
            if (status >= 200 && status < 300) {
                return SendResult.success(status, null, elapsed(started));
            }
            if (status == 404 || status == 410) {
                maintenance.expireDevice(request.tenantId(), device.getId());
                return SendResult.failure(false, true, status, "WEB_PUSH_SUBSCRIPTION_GONE",
                        "Push service reported the subscription gone.", null, elapsed(started));
            }
            return SendResult.failure(true, false, status, "WEB_PUSH_HTTP_" + status,
                    "Push service rejected the message.", null, elapsed(started));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return SendResult.failure(true, false, 0, "WEB_PUSH_INTERRUPTED", "Interrupted.", null, elapsed(started));
        } catch (Exception exception) {
            // Transport faults follow the outbox retry/backoff contract; the message never carries the endpoint.
            return SendResult.failure(true, false, 0, "WEB_PUSH_TRANSPORT_ERROR",
                    exception.getClass().getSimpleName(), null, elapsed(started));
        }
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

    private static PushService pushService(WebPushProperties properties) {
        if (!properties.isConfigured()) {
            return null;
        }
        try {
            return new PushService(properties.getPublicKey(), properties.getPrivateKey(), properties.getSubject());
        } catch (GeneralSecurityException | RuntimeException exception) {
            // Never include key material in the message.
            throw new IllegalStateException("Invalid VAPID key pair in erg.notifications.web-push.", null);
        }
    }

    private static long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
