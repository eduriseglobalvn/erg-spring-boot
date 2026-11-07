package com.erg.ergspringboot.notifications.persistence;

import com.erg.ergspringboot.notifications.persistence.projection.CountPair;
import com.erg.ergspringboot.notifications.persistence.projection.CampaignDeliveryCounts;
import com.erg.ergspringboot.notifications.persistence.projection.CampaignRecipientRow;
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

public interface NotificationMessageRepository extends JpaRepository<NotificationMessageEntity, String> {

    Optional<NotificationMessageEntity> findByTenantIdAndDedupeKey(String tenantId, String dedupeKey);

    /** First acknowledgement wins: only sets ackedAt while it is still null, and only on the user's own message. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationMessageEntity m
            set m.ackedAt = :ackedAt
            where m.id = :id
              and m.tenantId = :tenantId
              and m.ackedAt is null
              and exists (select r.id from NotificationRecipientEntity r
                          where r.notificationId = m.id and r.tenantId = :tenantId and r.userId = :userId)
            """)
    int ackOnce(@Param("tenantId") String tenantId, @Param("userId") String userId, @Param("id") String id,
                @Param("ackedAt") OffsetDateTime ackedAt);

    @Query("""
            select new com.erg.ergspringboot.notifications.persistence.projection.CampaignDeliveryCounts(
                m.campaignId, count(r.id),
                sum(case when r.readAt is not null then 1L else 0L end),
                sum(case when m.ackedAt is not null then 1L else 0L end))
            from NotificationMessageEntity m
            join NotificationRecipientEntity r on r.notificationId = m.id
            where m.tenantId = :tenantId and m.campaignId in :campaignIds
            group by m.campaignId
            """)
    List<CampaignDeliveryCounts> countByCampaigns(
            @Param("tenantId") String tenantId, @Param("campaignIds") Collection<String> campaignIds);

    @Query(value = """
            select new com.erg.ergspringboot.notifications.persistence.projection.CampaignRecipientRow(
                r.userId, r.readAt, m.ackedAt)
            from NotificationMessageEntity m
            join NotificationRecipientEntity r on r.notificationId = m.id
            where m.tenantId = :tenantId and m.campaignId = :campaignId
              and (:unreadOnly = false or r.readAt is null)
              and (:unackedOnly = false or m.ackedAt is null)
            order by r.userId asc
            """, countQuery = """
            select count(r.id)
            from NotificationMessageEntity m
            join NotificationRecipientEntity r on r.notificationId = m.id
            where m.tenantId = :tenantId and m.campaignId = :campaignId
              and (:unreadOnly = false or r.readAt is null)
              and (:unackedOnly = false or m.ackedAt is null)
            """)
    Page<CampaignRecipientRow> findCampaignRecipients(
            @Param("tenantId") String tenantId,
            @Param("campaignId") String campaignId,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("unackedOnly") boolean unackedOnly,
            Pageable pageable);

    /** (recipients, viewed) over campaigns sent since {@code since}. */
    @Query("""
            select new com.erg.ergspringboot.notifications.persistence.projection.CountPair(
                count(r.id), sum(case when r.readAt is not null then 1L else 0L end))
            from NotificationMessageEntity m
            join NotificationRecipientEntity r on r.notificationId = m.id
            join NotificationCampaignEntity c on c.id = m.campaignId
            where c.tenantId = :tenantId
              and c.status = com.erg.ergspringboot.notifications.CampaignStatus.SENT
              and c.sentAt >= :since
              and (:createdBy is null or c.createdBy = :createdBy)
            """)
    CountPair viewedSince(
            @Param("tenantId") String tenantId,
            @Param("since") OffsetDateTime since,
            @Param("createdBy") String createdBy);

    /** (unacked recipients, campaigns with at least one) over sent campaigns that require acknowledgement. */
    @Query("""
            select new com.erg.ergspringboot.notifications.persistence.projection.CountPair(
                count(m.id), count(distinct m.campaignId))
            from NotificationMessageEntity m
            join NotificationCampaignEntity c on c.id = m.campaignId
            where c.tenantId = :tenantId
              and c.status = com.erg.ergspringboot.notifications.CampaignStatus.SENT
              and c.requireReadAck = true
              and m.ackedAt is null
              and (:createdBy is null or c.createdBy = :createdBy)
            """)
    CountPair unacked(@Param("tenantId") String tenantId, @Param("createdBy") String createdBy);
}
