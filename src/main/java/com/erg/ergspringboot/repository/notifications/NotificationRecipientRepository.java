package com.erg.ergspringboot.repository.notifications;

import com.erg.ergspringboot.model.notifications.NotificationRecipientEntity;
import com.erg.ergspringboot.repository.notifications.projection.NotificationInboxProjection;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipientEntity, String> {

    @Query(
            value = """
                    select new com.erg.ergspringboot.repository.notifications.projection.NotificationInboxProjection(
                        recipient.id,
                        message.id,
                        recipient.tenantId,
                        recipient.userId,
                        recipient.portal,
                        recipient.status,
                        recipient.readAt,
                        recipient.archivedAt,
                        message.eventType,
                        message.sourceType,
                        message.sourceId,
                        message.title,
                        message.bodyPreview,
                        message.actionUrl,
                        message.priority,
                        message.channel,
                        message.payloadData,
                        recipient.createdAt
                    )
                    from NotificationRecipientEntity recipient
                    join NotificationMessageEntity message
                        on message.id = recipient.notificationId
                    where recipient.tenantId = :tenantId
                      and recipient.userId = :userId
                      and recipient.portal = :portal
                      and recipient.archivedAt is null
                      and (:status is null or recipient.status = :status)
                    order by recipient.createdAt desc
                    """,
            countQuery = """
                    select count(recipient.id)
                    from NotificationRecipientEntity recipient
                    where recipient.tenantId = :tenantId
                      and recipient.userId = :userId
                      and recipient.portal = :portal
                      and recipient.archivedAt is null
                      and (:status is null or recipient.status = :status)
                    """
    )
    Page<NotificationInboxProjection> findInbox(
            String tenantId,
            String userId,
            String portal,
            String status,
            Pageable pageable
    );

    @Query("""
            select new com.erg.ergspringboot.repository.notifications.projection.NotificationInboxProjection(
                recipient.id,
                message.id,
                recipient.tenantId,
                recipient.userId,
                recipient.portal,
                recipient.status,
                recipient.readAt,
                recipient.archivedAt,
                message.eventType,
                message.sourceType,
                message.sourceId,
                message.title,
                message.bodyPreview,
                message.actionUrl,
                message.priority,
                message.channel,
                message.payloadData,
                recipient.createdAt
            )
            from NotificationRecipientEntity recipient
            join NotificationMessageEntity message
                on message.id = recipient.notificationId
            where recipient.tenantId = :tenantId
              and recipient.userId = :userId
              and recipient.portal = :portal
              and (recipient.id = :id or recipient.notificationId = :id)
              and recipient.archivedAt is null
            """)
    Optional<NotificationInboxProjection> findOwnDetail(String tenantId, String userId, String portal, String id);

    long countByTenantIdAndUserIdAndPortalAndStatusAndArchivedAtIsNull(
            String tenantId,
            String userId,
            String portal,
            String status
    );

    Optional<NotificationRecipientEntity> findByTenantIdAndUserIdAndPortalAndId(
            String tenantId,
            String userId,
            String portal,
            String id
    );

    Optional<NotificationRecipientEntity> findByTenantIdAndUserIdAndPortalAndNotificationId(
            String tenantId,
            String userId,
            String portal,
            String notificationId
    );

    boolean existsByNotificationIdAndUserIdAndPortal(String notificationId, String userId, String portal);

    @Query("""
            select recipient.userId
            from NotificationRecipientEntity recipient
            where recipient.tenantId = :tenantId
              and recipient.notificationId = :notificationId
              and recipient.portal = :portal
              and recipient.userId in :userIds
            """)
    List<String> findExistingUserIds(
            @Param("tenantId") String tenantId,
            @Param("notificationId") String notificationId,
            @Param("portal") String portal,
            @Param("userIds") Collection<String> userIds
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationRecipientEntity recipient
            set recipient.status = 'read',
                recipient.readAt = :readAt
            where recipient.tenantId = :tenantId
              and recipient.userId = :userId
              and recipient.portal = :portal
              and recipient.status <> 'read'
              and recipient.archivedAt is null
            """)
    int markAllRead(String tenantId, String userId, String portal, OffsetDateTime readAt);
}
