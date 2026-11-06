package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.common.request.notifications.NotificationRequests.HeartbeatDeviceRequest;
import com.erg.ergspringboot.common.request.notifications.NotificationRequests.RegisterDeviceRequest;
import com.erg.ergspringboot.common.request.notifications.NotificationRequests.UnregisterDeviceRequest;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.DeviceRegistrationResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.WebPushConfigResponse;
import com.erg.ergspringboot.notifications.config.WebPushProperties;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationTopicMembershipEntity;
import com.erg.ergspringboot.notifications.NotificationTokenCipher;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationTopicMembershipRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeviceService {

    private final NotificationDeviceRegistrationRepository deviceRepository;
    private final NotificationTopicMembershipRepository topicMembershipRepository;
    private final NotificationTokenCipher tokenCipher;
    private final WebPushProperties webPush;

    public NotificationDeviceService(
            NotificationDeviceRegistrationRepository deviceRepository,
            NotificationTopicMembershipRepository topicMembershipRepository,
            NotificationTokenCipher tokenCipher,
            WebPushProperties webPush
    ) {
        this.deviceRepository = deviceRepository;
        this.topicMembershipRepository = topicMembershipRepository;
        this.tokenCipher = tokenCipher;
        this.webPush = webPush;
    }

    public WebPushConfigResponse webPushConfig() {
        boolean enabled = webPush.isConfigured();
        return new WebPushConfigResponse(enabled, enabled ? webPush.getPublicKey() : null);
    }

    @Transactional
    public DeviceRegistrationResponse register(String tenantId, String userId, String portal, RegisterDeviceRequest request) {
        String transport = transport(request.transport());
        boolean webPushTransport = WEB_PUSH.equals(transport);
        String fid = webPushTransport ? null : trimToNull(request.firebaseInstallationId());
        String token = trimToNull(request.registrationToken());
        String p256dh = null;
        String auth = null;
        if (webPushTransport) {
            if (!webPush.isConfigured()) {
                throw new BadRequestException("Web Push chưa được cấu hình");
            }
            // The endpoint is the subscription identity: stored and deduped exactly like an FCM token.
            token = WebPushSubscriptionValidator.endpoint(request.endpoint());
            p256dh = WebPushSubscriptionValidator.key(request.keys() == null ? null : request.keys().p256dh(),
                    WebPushSubscriptionValidator.P256DH_BYTES, "p256dh");
            auth = WebPushSubscriptionValidator.key(request.keys() == null ? null : request.keys().auth(),
                    WebPushSubscriptionValidator.AUTH_BYTES, "auth");
        }
        if (fid == null && token == null) {
            throw new BadRequestException("firebaseInstallationId or registrationToken is required.");
        }
        String tokenHash = token == null ? null : sha256(token);
        NotificationDeviceRegistrationEntity entity = fid == null
                ? deviceRepository.findByTenantIdAndRegistrationTokenHash(tenantId, tokenHash).orElseGet(NotificationDeviceRegistrationEntity::new)
                : deviceRepository.findByTenantIdAndFirebaseInstallationId(tenantId, fid).orElseGet(NotificationDeviceRegistrationEntity::new);
        assertBindableToUser(entity, userId);
        OffsetDateTime now = OffsetDateTime.now();
        entity.setTenantId(tenantId);
        entity.setUserId(userId);
        entity.setPortal(portal);
        entity.setPlatform(firstText(request.platform(), "web_pwa"));
        entity.setFirebaseInstallationId(fid);
        entity.setRegistrationToken(tokenCipher.encrypt(token));
        entity.setRegistrationTokenHash(tokenHash);
        entity.setEndpointHash(request.endpoint() == null ? null : sha256(webPushTransport ? token : request.endpoint()));
        entity.setTransport(transport);
        entity.setP256dh(tokenCipher.encrypt(p256dh));
        entity.setAuth(tokenCipher.encrypt(auth));
        entity.setServiceWorkerScope(trimToNull(request.serviceWorkerScope()));
        entity.setPermissionState(firstText(request.permissionState(), "granted"));
        entity.setLocale(trimToNull(request.locale()));
        entity.setTimezone(trimToNull(request.timezone()));
        if (request.client() != null) {
            entity.setBrowserName(trimToNull(request.client().browserName()));
            entity.setBrowserVersion(trimToNull(request.client().browserVersion()));
            entity.setOsName(trimToNull(request.client().osName()));
            entity.setUserAgentHash(request.client().userAgent() == null ? null : sha256(request.client().userAgent()));
        }
        entity.setEnabled(true);
        entity.setRevokedAt(null);
        entity.setLastSeenAt(now);
        entity.setTokenUpdatedAt(token == null ? entity.getTokenUpdatedAt() : now);
        NotificationDeviceRegistrationEntity saved = deviceRepository.save(entity);
        syncPortalTopic(tenantId, saved, now);
        return toResponse(saved);
    }

    @Transactional
    public void unregister(String tenantId, String userId, String portal, UnregisterDeviceRequest request) {
        String fid = trimToNull(request.firebaseInstallationId());
        String token = trimToNull(request.registrationToken());
        NotificationDeviceRegistrationEntity entity = null;
        if (fid != null) {
            entity = deviceRepository.findByTenantIdAndFirebaseInstallationId(tenantId, fid).orElse(null);
        }
        if (entity == null && token != null) {
            entity = deviceRepository.findByTenantIdAndRegistrationTokenHash(tenantId, sha256(token)).orElse(null);
        }
        if (entity == null || !userId.equals(entity.getUserId()) || !portal.equals(entity.getPortal())) {
            return;
        }
        revoke(entity);
    }

    @Transactional
    public DeviceRegistrationResponse heartbeat(String tenantId, String userId, String portal, HeartbeatDeviceRequest request) {
        NotificationDeviceRegistrationEntity entity = findHeartbeatDevice(tenantId, userId, portal, request);
        entity.setLastSeenAt(OffsetDateTime.now());
        if (request.permissionState() != null && !request.permissionState().isBlank()) {
            entity.setPermissionState(request.permissionState().trim().toLowerCase());
        }
        return toResponse(deviceRepository.save(entity));
    }

    @Transactional
    public void revokeOwnDevice(String tenantId, String userId, String portal, String id) {
        NotificationDeviceRegistrationEntity entity = deviceRepository.findByTenantIdAndUserIdAndPortalAndId(tenantId, userId, portal, id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification device not found."));
        revoke(entity);
    }

    private NotificationDeviceRegistrationEntity findHeartbeatDevice(
            String tenantId,
            String userId,
            String portal,
            HeartbeatDeviceRequest request
    ) {
        String id = trimToNull(request.deviceRegistrationId());
        if (id != null) {
            return deviceRepository.findByTenantIdAndUserIdAndPortalAndId(tenantId, userId, portal, id)
                    .orElseThrow(() -> new ResourceNotFoundException("Notification device not found."));
        }
        String fid = trimToNull(request.firebaseInstallationId());
        if (fid != null) {
            return deviceRepository.findByTenantIdAndFirebaseInstallationId(tenantId, fid)
                    .filter(device -> userId.equals(device.getUserId()) && portal.equals(device.getPortal()))
                    .orElseThrow(() -> new ResourceNotFoundException("Notification device not found."));
        }
        String token = trimToNull(request.registrationToken());
        if (token != null) {
            return deviceRepository.findByTenantIdAndRegistrationTokenHash(tenantId, sha256(token))
                    .filter(device -> userId.equals(device.getUserId()) && portal.equals(device.getPortal()))
                    .orElseThrow(() -> new ResourceNotFoundException("Notification device not found."));
        }
        throw new BadRequestException("deviceRegistrationId, firebaseInstallationId or registrationToken is required.");
    }

    private void assertBindableToUser(NotificationDeviceRegistrationEntity entity, String userId) {
        if (entity.getId() == null || userId.equals(entity.getUserId())) {
            return;
        }
        boolean revoked = entity.getRevokedAt() != null || Boolean.FALSE.equals(entity.getEnabled());
        if (!revoked) {
            throw new ForbiddenException("Notification device is already registered to another user.");
        }
    }

    private void revoke(NotificationDeviceRegistrationEntity entity) {
        OffsetDateTime now = OffsetDateTime.now();
        entity.setEnabled(false);
        entity.setRevokedAt(now);
        deviceRepository.save(entity);
    }

    private void syncPortalTopic(String tenantId, NotificationDeviceRegistrationEntity device, OffsetDateTime now) {
        String topicName = "t_" + sanitize(tenantId) + "_portal_" + sanitize(device.getPortal());
        NotificationTopicMembershipEntity membership = topicMembershipRepository
                .findByDeviceRegistrationIdAndTopicName(device.getId(), topicName)
                .orElseGet(NotificationTopicMembershipEntity::new);
        membership.setTenantId(tenantId);
        membership.setDeviceRegistrationId(device.getId());
        membership.setTopicName(topicName);
        membership.setMembershipStatus("active");
        membership.setSubscribedAt(membership.getSubscribedAt() == null ? now : membership.getSubscribedAt());
        membership.setLastSyncAt(now);
        topicMembershipRepository.save(membership);
    }

    private DeviceRegistrationResponse toResponse(NotificationDeviceRegistrationEntity entity) {
        return new DeviceRegistrationResponse(
                entity.getId(),
                entity.getPortal(),
                entity.getPlatform(),
                entity.getPermissionState(),
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getLastSeenAt(),
                entity.getTransport()
        );
    }

    private static final String WEB_PUSH = "WEB_PUSH";

    private String transport(String value) {
        String transport = value == null || value.isBlank() ? "FCM" : value.trim().toUpperCase(Locale.ROOT);
        if (!"FCM".equals(transport) && !WEB_PUSH.equals(transport)) {
            throw new BadRequestException("transport chỉ gồm FCM, WEB_PUSH");
        }
        return transport;
    }

    private String sanitize(String value) {
        return firstText(value, "unknown").toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
    }

    private String firstText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
