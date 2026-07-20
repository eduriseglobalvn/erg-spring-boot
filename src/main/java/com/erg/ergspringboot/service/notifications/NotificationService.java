package com.erg.ergspringboot.service.notifications;

import com.erg.ergspringboot.common.request.notifications.NotificationRequests.PreferenceUpdateItemRequest;
import com.erg.ergspringboot.common.response.PageResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.ArchiveResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.MarkReadResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.NotificationItemResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.NotificationPreferenceResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.UnreadCountResponse;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.notifications.NotificationPreferenceEntity;
import com.erg.ergspringboot.model.notifications.NotificationRecipientEntity;
import com.erg.ergspringboot.repository.notifications.NotificationPreferenceRepository;
import com.erg.ergspringboot.repository.notifications.NotificationRecipientRepository;
import com.erg.ergspringboot.repository.notifications.projection.NotificationInboxProjection;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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

    public NotificationService(
            NotificationRecipientRepository recipientRepository,
            NotificationPreferenceRepository preferenceRepository
    ) {
        this.recipientRepository = recipientRepository;
        this.preferenceRepository = preferenceRepository;
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
        return page(page.getContent().stream().map(this::toItem).toList(), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public NotificationItemResponse detail(String tenantId, String userId, String portal, String id) {
        return recipientRepository.findOwnDetail(tenantId, userId, portal, id)
                .map(this::toItem)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(String tenantId, String userId, String portal) {
        return new UnreadCountResponse(
                recipientRepository.countByTenantIdAndUserIdAndPortalAndStatusAndArchivedAtIsNull(
                        tenantId,
                        userId,
                        portal,
                        "unread"
                )
        );
    }

    @Transactional
    public MarkReadResponse markRead(String tenantId, String userId, String portal, String id) {
        NotificationRecipientEntity recipient = ownRecipient(tenantId, userId, portal, id);
        if (!"read".equals(recipient.getStatus())) {
            recipient.setStatus("read");
            recipient.setReadAt(OffsetDateTime.now());
            recipientRepository.save(recipient);
            return new MarkReadResponse(true, 1);
        }
        return new MarkReadResponse(true, 0);
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
        return recipientRepository.findByTenantIdAndUserIdAndPortalAndId(tenantId, userId, portal, id)
                .or(() -> recipientRepository.findByTenantIdAndUserIdAndPortalAndNotificationId(tenantId, userId, portal, id))
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
    }

    private NotificationItemResponse toItem(NotificationInboxProjection projection) {
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
                projection.payloadData() == null ? Map.of() : projection.payloadData()
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
