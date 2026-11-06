package com.erg.ergspringboot.notifications.persistence;

import com.erg.ergspringboot.notifications.CampaignStatus;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationCampaignRepository extends JpaRepository<NotificationCampaignEntity, String> {

    List<NotificationCampaignEntity> findByTenantIdAndStatusAndScheduledAtLessThanEqual(
            String tenantId, CampaignStatus status, OffsetDateTime now);

    Optional<NotificationCampaignEntity> findByTenantIdAndId(String tenantId, String id);

    /** Row lock for every write so an edit can never race the dispatch job into a second send. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from NotificationCampaignEntity c where c.tenantId = :tenantId and c.id = :id")
    Optional<NotificationCampaignEntity> lockByTenantIdAndId(@Param("tenantId") String tenantId, @Param("id") String id);

    /** Cross-tenant due list for the dispatch job (runs in maintenance tenant scope). */
    List<NotificationCampaignEntity> findTop100ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            CampaignStatus status, OffsetDateTime now);

    /** Conditional SCHEDULED -> SENDING claim; 0 rows means another run or a cancel got there first. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationCampaignEntity c
            set c.status = com.erg.ergspringboot.notifications.CampaignStatus.SENDING, c.updatedAt = :now
            where c.tenantId = :tenantId and c.id = :id
              and c.status = com.erg.ergspringboot.notifications.CampaignStatus.SCHEDULED
              and c.scheduledAt <= :now
            """)
    int claimDue(@Param("tenantId") String tenantId, @Param("id") String id, @Param("now") OffsetDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationCampaignEntity c
            set c.status = :to, c.updatedAt = :now
            where c.tenantId = :tenantId and c.id = :id and c.status in :from
            """)
    int transition(
            @Param("tenantId") String tenantId,
            @Param("id") String id,
            @Param("from") Collection<CampaignStatus> from,
            @Param("to") CampaignStatus to,
            @Param("now") OffsetDateTime now);

    @Query("""
            select c from NotificationCampaignEntity c
            where c.tenantId = :tenantId and c.status in :statuses
              and (:createdBy is null or c.createdBy = :createdBy)
            """)
    Page<NotificationCampaignEntity> findPage(
            @Param("tenantId") String tenantId,
            @Param("statuses") Collection<CampaignStatus> statuses,
            @Param("createdBy") String createdBy,
            Pageable pageable);

    @Query("""
            select count(c) from NotificationCampaignEntity c
            where c.tenantId = :tenantId
              and c.status = com.erg.ergspringboot.notifications.CampaignStatus.SENT
              and c.sentAt >= :since
              and (:createdBy is null or c.createdBy = :createdBy)
            """)
    long countSentSince(
            @Param("tenantId") String tenantId,
            @Param("since") OffsetDateTime since,
            @Param("createdBy") String createdBy);
}
