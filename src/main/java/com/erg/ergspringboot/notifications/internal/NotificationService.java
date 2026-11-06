package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.common.request.notifications.NotificationRequests.PreferenceUpdateItemRequest;
import com.erg.ergspringboot.common.response.PageResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.AckResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.ArchiveResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.MarkReadResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.NotificationItemResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.NotificationPreferenceResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.UnreadCountResponse;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.notifications.NotificationPriority;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationMessageEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationMessageRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationPreferenceEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationRecipientEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationPreferenceRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationRecipientRepository;
import com.erg.ergspringboot.notifications.persistence.projection.NotificationInboxProjection;
import com.erg.ergspringboot.users.UserAccountProjection;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.erg.ergspringboot.common.response.notifications.NotificationResponses.page;

@Service
public class NotificationService {

    private final NotificationRecipientRepository recipientRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationMessageRepository messageRepository;
    private final NotificationCampaignRepository campaignRepository;
    private final UserDirectoryPort users;

    public NotificationService(
            NotificationRecipientRepository recipientRepository,
            NotificationPreferenceRepository preferenceRepository,
            NotificationMessageRepository messageRepository,
            NotificationCampaignRepository campaignRepository,
            UserDirectoryPort users
    ) {
        this.recipientRepository = recipientRepository;
        this.preferenceRepository = preferenceRepository;
        this.messageRepository = messageRepository;
        this.campaignRepository = campaignRepository;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationItemResponse> listInbox(
            String tenantId,
            String userId,
            String portal,
            String status,
            int pageNumber,
            int pageSize
    ) {
        Pageable pageable = PageRequest.of(clamp(pageNumber, 0, 10_000), clamp(pageSize, 1, 100));
        Page<NotificationInboxProjection> page = recipientRepository.findInbox(
                tenantId,
                userId,
                portal,
                normalizedStatus(status),
                pageable
        );
        return page(toItems(tenantId, page.getContent()), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public NotificationItemResponse detail(String tenantId, String userId, String portal, String id) {
        List<NotificationInboxProjection> rows = recipientRepository.findOwnDetail(tenantId, userId, portal, id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Notification not found.");
        return toItems(tenantId, rows.subList(0, 1)).getFirst();
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(String tenantId, String userId, String portal) {
        return new UnreadCountResponse(recipientRepository.countUnread(tenantId, userId, portal));
    }

    @Transactional
    public MarkReadResponse markRead(String tenantId, String userId, String portal, String id) {
        NotificationRecipientEntity recipient = ownRecipient(tenantId, userId, portal, id);
        return new MarkReadResponse(true, markRead(recipient, OffsetDateTime.now()) ? 1 : 0);
    }

    /**
     * Read acknowledgement of a campaign message that asks for one. Idempotent: the first ackedAt is kept.
     * Acknowledging also marks the message read when it is still unread (an ack implies the user saw it).
     */
    @Transactional
    public AckResponse ack(String tenantId, String userId, String portal, String id) {
        NotificationRecipientEntity recipient = ownRecipient(tenantId, userId, portal, id);
        NotificationMessageEntity message = messageRepository.findById(recipient.getNotificationId())
                .filter(found -> tenantId.equals(found.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
        boolean requiresAck = message.getCampaignId() != null && campaignRepository
                .findByTenantIdAndId(tenantId, message.getCampaignId())
                .map(campaign -> campaign.isRequireReadAck())
                .orElse(false);
        if (!requiresAck) {
            throw new ConflictException("Thông báo này không yêu cầu xác nhận đã đọc");
        }
        // Microsecond precision = what Postgres stores, so a repeated ack returns the identical value.
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        markRead(recipient, now);
        String messageId = message.getId();
        // Conditional update: concurrent first acks cannot overwrite each other; return what is stored.
        messageRepository.ackOnce(tenantId, userId, messageId, now);
        OffsetDateTime ackedAt = messageRepository.findById(messageId).map(NotificationMessageEntity::getAckedAt)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
        return new AckResponse(messageId, ackedAt, recipient.getReadAt());
    }

    private boolean markRead(NotificationRecipientEntity recipient, OffsetDateTime now) {
        if ("read".equals(recipient.getStatus())) return false;
        recipient.setStatus("read");
        recipient.setReadAt(now);
        recipientRepository.save(recipient);
        return true;
    }

    @Transactional
    public MarkReadResponse markAllRead(String tenantId, String userId, String portal) {
        int updated = recipientRepository.markAllRead(tenantId, userId, portal, OffsetDateTime.now());
        return new MarkReadResponse(true, updated);
    }

    @Transactional
    public ArchiveResponse archive(String tenantId, String userId, String portal, String id) {
        NotificationRecipientEntity recipient = ownRecipient(tenantId, userId, portal, id);
        recipient.setArchivedAt(OffsetDateTime.now());
        recipientRepository.save(recipient);
        return new ArchiveResponse(true);
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> preferences(String tenantId, String userId, String portal) {
        return preferenceRepository.findByTenantIdAndUserIdAndPortalOrderByEventTypeAsc(tenantId, userId, portal)
                .stream()
                .map(this::toPreference)
                .toList();
    }

    @Transactional
    public List<NotificationPreferenceResponse> updatePreferences(
            String tenantId,
            String userId,
            String portal,
            List<PreferenceUpdateItemRequest> updates
    ) {
        if (updates == null || updates.isEmpty()) {
            return preferences(tenantId, userId, portal);
        }
        List<String> eventTypes = updates.stream().map(PreferenceUpdateItemRequest::eventType).distinct().toList();
        Map<String, NotificationPreferenceEntity> existing = preferenceRepository
                .findByTenantIdAndUserIdAndPortalAndEventTypeIn(tenantId, userId, portal, eventTypes)
                .stream()
                .collect(java.util.stream.Collectors.toMap(NotificationPreferenceEntity::getEventType,
                        value -> value, (left, right) -> left));
        for (PreferenceUpdateItemRequest update : updates) {
            NotificationPreferenceEntity entity = existing.computeIfAbsent(update.eventType(), ignored -> new NotificationPreferenceEntity());
            entity.setTenantId(tenantId);
            entity.setUserId(userId);
            entity.setPortal(portal);
            entity.setEventType(update.eventType());
            if (update.pushEnabled() != null) {
                entity.setPushEnabled(update.pushEnabled());
            }
            if (update.inboxEnabled() != null) {
                entity.setInboxEnabled(update.inboxEnabled());
            }
            if (update.emailEnabled() != null) {
                entity.setEmailEnabled(update.emailEnabled());
            }
            if (update.quietHours() != null) {
                entity.setQuietHours(update.quietHours());
            }
        }
        if (!existing.isEmpty()) preferenceRepository.saveAll(existing.values());
        return preferences(tenantId, userId, portal);
    }

    private NotificationRecipientEntity ownRecipient(String tenantId, String userId, String portal, String id) {
        return recipientRepository.findOwn(tenantId, userId, portal, id).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
    }

    /** Maps rows; campaign sender names are looked up in one batch per call (no N+1). */
    private List<NotificationItemResponse> toItems(String tenantId, List<NotificationInboxProjection> rows) {
        List<String> senderIds = rows.stream().map(NotificationInboxProjection::campaignCreatedBy)
                .filter(Objects::nonNull).distinct().toList();
        Map<String, String> names = senderIds.isEmpty() ? Map.of() : users.findByTenantIdAndIds(tenantId, senderIds).stream()
                .filter(user -> user.displayName() != null)
                .collect(Collectors.toMap(UserAccountProjection::id, UserAccountProjection::displayName, (a, b) -> a));
        return rows.stream().map(row -> toItem(row,
                row.campaignCreatedBy() == null ? null : names.get(row.campaignCreatedBy()))).toList();
    }

    private NotificationItemResponse toItem(NotificationInboxProjection projection, String senderName) {
        return new NotificationItemResponse(
                projection.notificationId(),
                projection.recipientId(),
                projection.eventType(),
                projection.sourceType(),
                projection.sourceId(),
                projection.title(),
                projection.bodyPreview(),
                projection.actionUrl(),
                projection.priority(),
                projection.channel(),
                projection.recipientStatus(),
                projection.readAt(),
                projection.createdAt(),
                projection.payloadData() == null ? Map.of() : projection.payloadData(),
                NotificationPriority.fromStored(projection.priority()),
                Boolean.TRUE.equals(projection.campaignRequiresAck()),
                projection.ackedAt(),
                projection.campaignId(),
                projection.campaignId() == null ? null : senderName
        );
    }

    private NotificationPreferenceResponse toPreference(NotificationPreferenceEntity entity) {
        return new NotificationPreferenceResponse(
                entity.getEventType(),
                Boolean.TRUE.equals(entity.getPushEnabled()),
                Boolean.TRUE.equals(entity.getInboxEnabled()),
                Boolean.TRUE.equals(entity.getEmailEnabled()),
                entity.getQuietHours() == null ? Map.of() : entity.getQuietHours()
        );
    }

    private String normalizedStatus(String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return null;
        }
        String normalized = status.trim().toLowerCase();
        return "unread".equals(normalized) || "read".equals(normalized) ? normalized : null;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
