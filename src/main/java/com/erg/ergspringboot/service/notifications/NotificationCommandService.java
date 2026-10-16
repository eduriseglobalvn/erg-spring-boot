package com.erg.ergspringboot.service.notifications;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.notifications.NotificationDeviceRegistrationEntity;
import com.erg.ergspringboot.model.notifications.NotificationMessageEntity;
import com.erg.ergspringboot.model.notifications.NotificationOutboxEntity;
import com.erg.ergspringboot.model.notifications.NotificationRecipientEntity;
import com.erg.ergspringboot.notifications.NotificationTokenCipher;
import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.repository.notifications.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.repository.notifications.NotificationMessageRepository;
import com.erg.ergspringboot.repository.notifications.NotificationOutboxRepository;
import com.erg.ergspringboot.repository.notifications.NotificationRecipientRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationCommandService {

    private static final Set<String> RESERVED_DATA_KEYS = Set.of(
            "notificationId",
            "eventType",
            "tenantId",
            "portal",
            "sourceType",
            "sourceId",
            "deeplink",
            "deviceRegistrationId"
    );

    private final NotificationMessageRepository messageRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final NotificationDeviceRegistrationRepository deviceRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationPushProperties properties;
    private final NotificationTokenCipher tokenCipher;
    private final NotificationMetrics metrics;

    public NotificationCommandService(
            NotificationMessageRepository messageRepository,
            NotificationRecipientRepository recipientRepository,
            NotificationDeviceRegistrationRepository deviceRepository,
            NotificationOutboxRepository outboxRepository,
            NotificationPushProperties properties,
            NotificationTokenCipher tokenCipher,
            NotificationMetrics metrics
    ) {
        this.messageRepository = messageRepository;
        this.recipientRepository = recipientRepository;
        this.deviceRepository = deviceRepository;
        this.outboxRepository = outboxRepository;
        this.properties = properties;
        this.tokenCipher = tokenCipher;
        this.metrics = metrics;
    }

    @Transactional
    public NotificationCommandResult enqueue(NotificationCommand command) {
        Set<String> userIds = normalizedUserIds(command.userIds());
        String topicName = normalizedTopicName(command.tenantId(), command.topicName());
        validateTargets(userIds, topicName);
        validatePayloadData(command.data());

        String dedupeKey = trimToNull(command.idempotencyKey());
        if (dedupeKey != null) {
            java.util.Optional<NotificationMessageEntity> existing = messageRepository.findByTenantIdAndDedupeKey(
                    command.tenantId(),
                    dedupeKey
            );
            if (existing.isPresent()) {
                return new NotificationCommandResult(existing.get().getId(), 0, 0);
            }
        }

        NotificationMessageEntity message = new NotificationMessageEntity();
        message.setTenantId(command.tenantId());
        message.setEventType(firstText(command.eventType(), "GENERAL_NOTICE"));
        message.setSourceType(trimToNull(command.sourceType()));
        message.setSourceId(trimToNull(command.sourceId()));
        message.setTitle(command.title());
        message.setBodyPreview(command.bodyPreview());
        message.setActionUrl(trimToNull(command.actionUrl()));
        message.setPriority(firstText(command.priority(), "normal"));
        message.setChannel("push");
        message.setVisibility("private");
        message.setDedupeKey(dedupeKey);
        message.setCreatedBy(trimToNull(command.createdBy()));
        message.setPayloadData(command.data() == null ? Map.of() : command.data());
        NotificationMessageEntity savedMessage = messageRepository.saveAndFlush(message);

        int recipientCount = 0;
        int outboxCount = 0;
        String portal = firstText(command.portal(), "lms");
        List<String> existingUserIds = userIds.isEmpty()
                ? List.of()
                : recipientRepository.findExistingUserIds(command.tenantId(), savedMessage.getId(), portal, userIds);
        java.util.Set<String> existing = new java.util.HashSet<>(existingUserIds);
        List<NotificationRecipientEntity> newRecipients = new ArrayList<>();
        for (String userId : userIds) {
            if (!existing.contains(userId)) {
                NotificationRecipientEntity recipient = new NotificationRecipientEntity();
                recipient.setTenantId(command.tenantId());
                recipient.setNotificationId(savedMessage.getId());
                recipient.setUserId(userId);
                recipient.setPortal(portal);
                newRecipients.add(recipient);
            }
        }
        if (!newRecipients.isEmpty()) {
            recipientRepository.saveAll(newRecipients);
            recipientCount = newRecipients.size();
        }

        List<NotificationOutboxEntity> outboxes = new ArrayList<>();
        if (!userIds.isEmpty()) {
            List<NotificationDeviceRegistrationEntity> devices =
                    deviceRepository.findByTenantIdAndUserIdInAndPortalAndEnabledTrue(
                            command.tenantId(), userIds, portal);
            for (NotificationDeviceRegistrationEntity device : devices) {
                NotificationOutboxEntity outbox = outboxForDevice(savedMessage, command, device);
                if (outbox != null) outboxes.add(outbox);
            }
        }
        if (!outboxes.isEmpty()) {
            outboxRepository.saveAll(outboxes);
            outboxCount += outboxes.size();
        }

        if (topicName != null) {
            outboxRepository.save(outboxForTopic(savedMessage, command, topicName));
            outboxCount++;
        }

        metrics.created(savedMessage.getEventType(), firstText(command.portal(), "lms"), recipientCount, outboxCount);
        return new NotificationCommandResult(savedMessage.getId(), recipientCount, outboxCount);
    }

    private void validateTargets(Set<String> userIds, String topicName) {
        if (userIds.isEmpty() && topicName == null) {
            throw new BadRequestException("At least one notification userId or tenant-scoped topicName is required.");
        }
    }

    private void validatePayloadData(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        for (String key : data.keySet()) {
            if (key == null || key.isBlank()) {
                throw new BadRequestException("Notification data keys must not be blank.");
            }
            if (RESERVED_DATA_KEYS.contains(key.trim())) {
                throw new BadRequestException("Notification data key is reserved: " + key.trim());
            }
        }
    }

    private Set<String> normalizedUserIds(List<String> userIds) {
        Set<String> normalized = new LinkedHashSet<>();
        if (userIds == null) {
            return normalized;
        }
        for (String userId : userIds) {
            String trimmed = trimToNull(userId);
            if (trimmed != null) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private String normalizedTopicName(String tenantId, String topicName) {
        String trimmed = trimToNull(topicName);
        if (trimmed == null) {
            return null;
        }
        String topic = trimmed.startsWith("/topics/") ? trimmed.substring("/topics/".length()) : trimmed;
        String normalized = topic.toLowerCase();
        if (!normalized.matches("[a-z0-9_.~%-]+")) {
            throw new BadRequestException("Firebase topicName contains unsupported characters.");
        }
        String requiredPrefix = "t_" + sanitize(tenantId) + "_";
        if (!normalized.startsWith(requiredPrefix)) {
            throw new BadRequestException("Firebase topicName must be scoped to the current tenant namespace.");
        }
        return normalized;
    }

    private NotificationOutboxEntity outboxForDevice(
            NotificationMessageEntity message,
            NotificationCommand command,
            NotificationDeviceRegistrationEntity device
    ) {
        String token = trimToNull(device.getRegistrationToken());
        String fid = trimToNull(device.getFirebaseInstallationId());
        if (token == null && fid == null) {
            return null;
        }
        String targetKind = token == null ? "fid" : "registration_token";
        String targetRef = token == null ? fid : tokenCipher.encrypt(token);
        Map<String, String> payload = payload(message, command, Map.of("deviceRegistrationId", device.getId()));
        return outbox(message, targetKind, targetRef, payload);
    }

    private NotificationOutboxEntity outboxForTopic(NotificationMessageEntity message, NotificationCommand command, String topicName) {
        return outbox(message, "topic", topicName, payload(message, command, Map.of()));
    }

    private NotificationOutboxEntity outbox(
            NotificationMessageEntity message,
            String targetKind,
            String targetRef,
            Map<String, String> payload
    ) {
        NotificationOutboxEntity outbox = new NotificationOutboxEntity();
        outbox.setTenantId(message.getTenantId());
        outbox.setNotificationId(message.getId());
        outbox.setTargetKind(targetKind);
        outbox.setTargetRef(targetRef);
        outbox.setProvider("firebase");
        outbox.setPayload(payload);
        outbox.setStatus("pending");
        outbox.setAttemptCount(0);
        outbox.setMaxAttempts(properties.getWorker().getMaxAttempts());
        outbox.setNextAttemptAt(OffsetDateTime.now());
        return outbox;
    }

    private Map<String, String> payload(
            NotificationMessageEntity message,
            NotificationCommand command,
            Map<String, String> extra
    ) {
        Map<String, String> data = new java.util.LinkedHashMap<>();
        data.put("notificationId", message.getId());
        data.put("eventType", message.getEventType());
        data.put("tenantId", message.getTenantId());
        data.put("portal", firstText(command.portal(), "lms"));
        putIfPresent(data, "sourceType", message.getSourceType());
        putIfPresent(data, "sourceId", message.getSourceId());
        putIfPresent(data, "deeplink", message.getActionUrl());
        if (message.getPayloadData() != null) {
            data.putAll(message.getPayloadData());
        }
        data.putAll(extra);
        return data;
    }

    private void putIfPresent(Map<String, String> data, String key, String value) {
        if (value != null && !value.isBlank()) {
            data.put(key, value);
        }
    }

    private String firstText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String sanitize(String value) {
        return firstText(value, "unknown").toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
    }
}
