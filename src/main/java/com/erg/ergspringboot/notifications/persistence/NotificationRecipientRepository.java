package com.erg.ergspringboot.notifications.persistence;

import com.erg.ergspringboot.notifications.persistence.NotificationRecipientEntity;
import com.erg.ergspringboot.notifications.persistence.projection.NotificationInboxProjection;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Recipient inbox. Every read/write is scoped by tenant and user. The portal filter applies to legacy
 * producers' rows only: campaign rows (message.campaignId set) are written once per user with portal 'lms'
 * and must reach the user from whichever portal their session holds (students use 'elearning').
 */
public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipientEntity, String> {

    String INBOX_ITEM = """
            select new com.erg.ergspringboot.notifications.persistence.projection.NotificationInboxProjection(
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
                recipient.createdAt,
                message.campaignId,
                message.ackedAt,
                campaign.requireReadAck,
                campaign.createdBy
            )
            from NotificationRecipientEntity recipient
            join NotificationMessageEntity message
                on message.id = recipient.notificationId
            left join NotificationCampaignEntity campaign
                on campaign.id = message.campaignId and campaign.tenantId = recipient.tenantId
            """;

    String OWN_ROWS = """
              recipient.tenantId = :tenantId
              and recipient.userId = :userId
              and (recipient.portal = :portal or message.campaignId is not null)
              and recipient.archivedAt is null
            """;

    @Query(
            value = INBOX_ITEM + " where " + OWN_ROWS + """
                      and (:status is null or recipient.status = :status)
                    order by recipient.createdAt desc
                    """,
            countQuery = """
                    select count(recipient.id)
                    from NotificationRecipientEntity recipient
                    join NotificationMessageEntity message
                        on message.id = recipient.notificationId
                    where """ + OWN_ROWS + """
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

    @Query(INBOX_ITEM + " where " + OWN_ROWS + " and (recipient.id = :id or recipient.notificationId = :id)")
    List<NotificationInboxProjection> findOwnDetail(String tenantId, String userId, String portal, String id);

    @Query("""
            select count(recipient.id)
            from NotificationRecipientEntity recipient
            join NotificationMessageEntity message
                on message.id = recipient.notificationId
            where """ + OWN_ROWS + " and recipient.status = 'unread'")
    long countUnread(String tenantId, String userId, String portal);

    /** Own recipient row by recipient id or notification id; same visibility as the inbox. */
    @Query("""
            select recipient
            from NotificationRecipientEntity recipient
            join NotificationMessageEntity message
                on message.id = recipient.notificationId
            where """ + OWN_ROWS + """
              and (recipient.id = :id or recipient.notificationId = :id)
            order by case when recipient.id = :id then 0 else 1 end
            """)
    List<NotificationRecipientEntity> findOwn(String tenantId, String userId, String portal, String id);

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
              and (recipient.portal = :portal
                   or recipient.notificationId in (
                       select message.id from NotificationMessageEntity message
                       where message.tenantId = :tenantId and message.campaignId is not null))
              and recipient.status <> 'read'
              and recipient.archivedAt is null
            """)
    int markAllRead(String tenantId, String userId, String portal, OffsetDateTime readAt);
}
