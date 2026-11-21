package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationMessageEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationOutboxEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationPreferenceEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationRecipientEntity;
import com.erg.ergspringboot.notifications.NotificationTokenCipher;
import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationMessageRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationOutboxRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationPreferenceRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationRecipientRepository;
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

    public static final String CAMPAIGN_EVENT_TYPE = "CAMPAIGN";
    public static final String CAMPAIGN_PORTAL = "lms";
    /** Outbox payload marker: URGENT campaigns are pushed even to users who opted out of campaign pushes. */
    public static final String BYPASS_PREFERENCES_KEY = "bypassPreferences";
    private static final int BODY_PREVIEW_MAX = 500;

    private final NotificationMessageRepository messageRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final NotificationDeviceRegistrationRepository deviceRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationPushProperties properties;
    private final NotificationTokenCipher tokenCipher;
    private final NotificationMetrics metrics;
    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationCommandService(
            NotificationMessageRepository messageRepository,
            NotificationRecipientRepository recipientRepository,
            NotificationDeviceRegistrationRepository deviceRepository,
            NotificationOutboxRepository outboxRepository,
            NotificationPushProperties properties,
            NotificationTokenCipher tokenCipher,
            NotificationMetrics metrics,
            NotificationPreferenceRepository preferenceRepository
    ) {
        this.messageRepository = messageRepository;
        this.recipientRepository = recipientRepository;
        this.deviceRepository = deviceRepository;
        this.outboxRepository = outboxRepository;
        this.properties = properties;
        this.tokenCipher = tokenCipher;
        this.metrics = metrics;
        this.preferenceRepository = preferenceRepository;
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

        NotificationMessageEntity savedMessage = messageRepository.saveAndFlush(newMessage(command, dedupeKey));

        int recipientCount = 0;
        int outboxCount = 0;
        String portal = firstText(command.portal(), "lms");
        // Per-user, per-event-type opt-outs: inbox opt-out drops the recipient row, push
        // opt-out drops that user's device outbox rows. Absent preference means enabled.
        Set<String> inboxDisabled = new java.util.HashSet<>();
        Set<String> pushDisabled = new java.util.HashSet<>();
        if (!userIds.isEmpty()) {
            for (NotificationPreferenceEntity preference : preferenceRepository
                    .findByTenantIdAndPortalAndEventTypeAndUserIdIn(
                            command.tenantId(), portal, savedMessage.getEventType(), userIds)) {
                if (Boolean.FALSE.equals(preference.getInboxEnabled())) inboxDisabled.add(preference.getUserId());
                if (Boolean.FALSE.equals(preference.getPushEnabled())) pushDisabled.add(preference.getUserId());
            }
        }
        List<String> existingUserIds = userIds.isEmpty()
                ? List.of()
                : recipientRepository.findExistingUserIds(command.tenantId(), savedMessage.getId(), portal, userIds);
        java.util.Set<String> existing = new java.util.HashSet<>(existingUserIds);
        List<NotificationRecipientEntity> newRecipients = new ArrayList<>();
        for (String userId : userIds) {
            if (!existing.contains(userId) && !inboxDisabled.contains(userId)) {
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
            // Students enable push from elearning, so an "lms" event must reach devices of every portal.
            for (NotificationDeviceRegistrationEntity device :
                    deviceRepository.findByTenantIdAndUserIdInAndEnabledTrue(command.tenantId(), userIds)) {
                if (pushDisabled.contains(device.getUserId()) || !properties.sendsTransport(device.getTransport())) continue;
                NotificationOutboxEntity outbox = outboxForDevice(savedMessage, withPortal(command, device.getPortal()), device);
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

    /**
     * Same semantics as {@link #enqueue} for many commands of one listener (schedule changes: one per teacher),
     * with the dedupe, preference and device lookups and the inserts batched per tenant instead of ~7 queries
     * per recipient. Dedupe keys already stored, or repeated inside the call, are skipped like a retry.
     */
    @Transactional
    public void enqueueAll(List<NotificationCommand> commands) {
        Map<String, List<NotificationCommand>> byTenant = new java.util.LinkedHashMap<>();
        for (NotificationCommand command : commands) {
            byTenant.computeIfAbsent(command.tenantId(), tenant -> new ArrayList<>()).add(command);
        }
        byTenant.forEach(this::enqueueTenant);
    }

    private record Prepared(NotificationCommand command, Set<String> userIds, String topicName, String dedupeKey) {}

    private void enqueueTenant(String tenantId, List<NotificationCommand> commands) {
        List<Prepared> prepared = new ArrayList<>();
        for (NotificationCommand command : commands) {
            Set<String> userIds = normalizedUserIds(command.userIds());
            String topicName = normalizedTopicName(command.tenantId(), command.topicName());
            validateTargets(userIds, topicName);
            validatePayloadData(command.data());
            prepared.add(new Prepared(command, userIds, topicName, trimToNull(command.idempotencyKey())));
        }
        Set<String> keys = new java.util.HashSet<>();
        prepared.forEach(p -> {
            if (p.dedupeKey() != null) keys.add(p.dedupeKey());
        });
        Set<String> stored = new java.util.HashSet<>();
        if (!keys.isEmpty()) {
            messageRepository.findByTenantIdAndDedupeKeyIn(tenantId, keys).forEach(m -> stored.add(m.getDedupeKey()));
        }
        List<Prepared> fresh = new ArrayList<>();
        for (Prepared p : prepared) {
            if (p.dedupeKey() == null || stored.add(p.dedupeKey())) fresh.add(p);
        }
        if (fresh.isEmpty()) return;

        List<NotificationMessageEntity> messages = fresh.stream().map(p -> newMessage(p.command(), p.dedupeKey())).toList();
        messageRepository.saveAllAndFlush(messages);

        // One preference lookup per (portal, event type) and one device lookup for everybody.
        Map<String, Set<String>> usersByScope = new java.util.LinkedHashMap<>();
        Set<String> allUserIds = new LinkedHashSet<>();
        for (int i = 0; i < fresh.size(); i++) {
            usersByScope.computeIfAbsent(preferenceScope(fresh.get(i).command(), messages.get(i)), k -> new LinkedHashSet<>())
                    .addAll(fresh.get(i).userIds());
            allUserIds.addAll(fresh.get(i).userIds());
        }
        Map<String, Set<String>> inboxDisabled = new java.util.HashMap<>();
        Map<String, Set<String>> pushDisabled = new java.util.HashMap<>();
        usersByScope.forEach((scope, userIds) -> {
            if (userIds.isEmpty()) return;
            String[] parts = scope.split("\u0001", 2);
            for (NotificationPreferenceEntity preference : preferenceRepository
                    .findByTenantIdAndPortalAndEventTypeAndUserIdIn(tenantId, parts[0], parts[1], userIds)) {
                if (Boolean.FALSE.equals(preference.getInboxEnabled())) {
                    inboxDisabled.computeIfAbsent(scope, k -> new java.util.HashSet<>()).add(preference.getUserId());
                }
                if (Boolean.FALSE.equals(preference.getPushEnabled())) {
                    pushDisabled.computeIfAbsent(scope, k -> new java.util.HashSet<>()).add(preference.getUserId());
                }
            }
        });
        Map<String, List<NotificationDeviceRegistrationEntity>> devices = new java.util.HashMap<>();
        if (!allUserIds.isEmpty()) {
            deviceRepository.findByTenantIdAndUserIdInAndEnabledTrue(tenantId, allUserIds)
                    .forEach(device -> devices.computeIfAbsent(device.getUserId(), k -> new ArrayList<>()).add(device));
        }

        List<NotificationRecipientEntity> recipients = new ArrayList<>();
        List<NotificationOutboxEntity> outboxes = new ArrayList<>();
        for (int i = 0; i < fresh.size(); i++) {
            Prepared p = fresh.get(i);
            NotificationMessageEntity message = messages.get(i);
            String portal = firstText(p.command().portal(), "lms");
            String scope = preferenceScope(p.command(), message);
            int recipientCount = 0;
            int outboxCount = 0;
            for (String userId : p.userIds()) {
                if (!inboxDisabled.getOrDefault(scope, Set.of()).contains(userId)) {
                    NotificationRecipientEntity recipient = new NotificationRecipientEntity();
                    recipient.setTenantId(tenantId);
                    recipient.setNotificationId(message.getId());
                    recipient.setUserId(userId);
                    recipient.setPortal(portal);
                    recipients.add(recipient);
                    recipientCount++;
                }
                if (pushDisabled.getOrDefault(scope, Set.of()).contains(userId)) continue;
                for (NotificationDeviceRegistrationEntity device : devices.getOrDefault(userId, List.of())) {
                    if (!properties.sendsTransport(device.getTransport())) continue;
                    NotificationOutboxEntity outbox = outboxForDevice(message, withPortal(p.command(), device.getPortal()), device);
                    if (outbox != null) {
                        outboxes.add(outbox);
                        outboxCount++;
                    }
                }
            }
            if (p.topicName() != null) {
                outboxes.add(outboxForTopic(message, p.command(), p.topicName()));
                outboxCount++;
            }
            metrics.created(message.getEventType(), portal, recipientCount, outboxCount);
        }
        if (!recipients.isEmpty()) recipientRepository.saveAll(recipients);
        if (!outboxes.isEmpty()) outboxRepository.saveAll(outboxes);
    }

    private String preferenceScope(NotificationCommand command, NotificationMessageEntity message) {
        return firstText(command.portal(), "lms") + "\u0001" + message.getEventType();
    }

    private NotificationMessageEntity newMessage(NotificationCommand command, String dedupeKey) {
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
        message.setCreatedBy(actorOrNull(command.createdBy()));
        message.setPayloadData(command.data() == null ? Map.of() : command.data());
        return message;
    }

    /** At most {@value #BODY_PREVIEW_MAX} chars, never cutting a surrogate pair (emoji) in half. */
    static String bodyPreview(String body) {
        if (body.length() <= BODY_PREVIEW_MAX) {
            return body;
        }
        int end = Character.isHighSurrogate(body.charAt(BODY_PREVIEW_MAX - 1)) ? BODY_PREVIEW_MAX - 1 : BODY_PREVIEW_MAX;
        return body.substring(0, end);
    }

    /** Campaign send: inbox is always on; push honours per-user opt-outs unless the campaign is URGENT. */
    @Transactional
    public NotificationCommandResult enqueueCampaign(CampaignDelivery delivery) {
        boolean urgent = delivery.priority() == com.erg.ergspringboot.notifications.NotificationPriority.URGENT;
        Map<String, String> data = new java.util.LinkedHashMap<>();
        data.put("campaignId", delivery.campaignId());
        data.put("priority", delivery.priority().name());
        if (urgent) data.put(BYPASS_PREFERENCES_KEY, "true");
        String preview = bodyPreview(delivery.body());
        NotificationCommand command = new NotificationCommand(delivery.tenantId(), delivery.createdBy(), null,
                CAMPAIGN_EVENT_TYPE, "notification_campaign", delivery.campaignId(), delivery.title(), preview,
                null, delivery.priority().name(), CAMPAIGN_PORTAL, delivery.userIds(), null, data);
        Set<String> userIds = normalizedUserIds(delivery.userIds());
        Map<String, NotificationMessageEntity> messages = new java.util.LinkedHashMap<>();
        for (String userId : userIds) {
            NotificationMessageEntity message = new NotificationMessageEntity();
            message.setTenantId(delivery.tenantId());
            message.setEventType(CAMPAIGN_EVENT_TYPE);
            message.setSourceType(command.sourceType());
            message.setSourceId(delivery.campaignId());
            message.setCampaignId(delivery.campaignId());
            message.setTitle(delivery.title());
            message.setBodyPreview(preview);
            message.setPriority(delivery.priority().name());
            message.setChannel(delivery.pushEnabled() ? "push" : "inbox");
            message.setVisibility("private");
            message.setCreatedBy(actorOrNull(delivery.createdBy()));
            message.setPayloadData(data);
            messages.put(userId, message);
        }
        // Persist (not merge): ids are assigned by @PrePersist, so recipients are linked after saving.
        messageRepository.saveAll(messages.values());
        List<NotificationRecipientEntity> recipients = new ArrayList<>();
        messages.forEach((userId, message) -> {
            NotificationRecipientEntity recipient = new NotificationRecipientEntity();
            recipient.setTenantId(delivery.tenantId());
            recipient.setNotificationId(message.getId());
            recipient.setUserId(userId);
            recipient.setPortal(CAMPAIGN_PORTAL);
            recipients.add(recipient);
        });
        recipientRepository.saveAll(recipients);

        List<NotificationOutboxEntity> outboxes = new ArrayList<>();
        if (delivery.pushEnabled() && !userIds.isEmpty()) {
            // Devices register under the portal of the session that enabled push (students: elearning, teachers:
            // lms, ...), so look across portals and honour the opt-out stored for that same portal.
            Set<String> pushDisabled = new java.util.HashSet<>();
            if (!urgent) {
                preferenceRepository.findByTenantIdAndEventTypeAndUserIdIn(
                                delivery.tenantId(), CAMPAIGN_EVENT_TYPE, userIds).stream()
                        .filter(preference -> Boolean.FALSE.equals(preference.getPushEnabled()))
                        .forEach(preference -> pushDisabled.add(preference.getUserId() + "|" + preference.getPortal()));
            }
            for (NotificationDeviceRegistrationEntity device : deviceRepository
                    .findByTenantIdAndUserIdInAndEnabledTrue(delivery.tenantId(), userIds)) {
                NotificationMessageEntity message = messages.get(device.getUserId());
                if (message == null || pushDisabled.contains(device.getUserId() + "|" + device.getPortal())
                        || !properties.sendsTransport(device.getTransport())) continue;
                NotificationOutboxEntity outbox = outboxForDevice(message, withPortal(command, device.getPortal()), device);
                if (outbox != null) outboxes.add(outbox);
            }
            if (!outboxes.isEmpty()) outboxRepository.saveAll(outboxes);
        }
        metrics.created(CAMPAIGN_EVENT_TYPE, CAMPAIGN_PORTAL, recipients.size(), outboxes.size());
        return new NotificationCommandResult(delivery.campaignId(), recipients.size(), outboxes.size());
    }

    /** The push payload carries the device's portal so the SW/tab handlers of that portal react to it. */
    private static NotificationCommand withPortal(NotificationCommand c, String portal) {
        return new NotificationCommand(c.tenantId(), c.createdBy(), c.idempotencyKey(), c.eventType(), c.sourceType(),
                c.sourceId(), c.title(), c.bodyPreview(), c.actionUrl(), c.priority(), portal, c.userIds(),
                c.topicName(), c.data());
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
        Map<String, String> payload = payload(message, command, Map.of("deviceRegistrationId", device.getId()));
        if ("WEB_PUSH".equals(device.getTransport())) {
            // Endpoint and keys stay encrypted on the device row; the adapter loads them by device id.
            return outbox(message, "web_push", device.getId(), payload, "vapid");
        }
        String targetKind = token == null ? "fid" : "registration_token";
        String targetRef = token == null ? fid : tokenCipher.encrypt(token);
        return outbox(message, targetKind, targetRef, payload, "firebase");
    }

    private NotificationOutboxEntity outboxForTopic(NotificationMessageEntity message, NotificationCommand command, String topicName) {
        return outbox(message, "topic", topicName, payload(message, command, Map.of()), "firebase");
    }

    private NotificationOutboxEntity outbox(
            NotificationMessageEntity message,
            String targetKind,
            String targetRef,
            Map<String, String> payload,
            String provider
    ) {
        NotificationOutboxEntity outbox = new NotificationOutboxEntity();
        outbox.setTenantId(message.getTenantId());
        outbox.setNotificationId(message.getId());
        outbox.setTargetKind(targetKind);
        outbox.setTargetRef(targetRef);
        outbox.setProvider(provider);
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

    /** "system" marks a job, not a users row; created_by is a nullable FK to users. */
    private String actorOrNull(String actor) {
        String trimmed = trimToNull(actor);
        return "system".equals(trimmed) ? null : trimmed;
    }
}
