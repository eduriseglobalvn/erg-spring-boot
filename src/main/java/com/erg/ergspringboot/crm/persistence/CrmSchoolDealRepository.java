package com.erg.ergspringboot.crm.persistence;

import com.erg.ergspringboot.crm.persistence.CrmSchoolDeal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrmSchoolDealRepository extends JpaRepository<CrmSchoolDeal, String> {

    Optional<CrmSchoolDeal> findByIdAndTenantId(String id, String tenantId);

    List<CrmSchoolDeal> findByTenantIdAndSchoolIdInOrderByCreatedAtDesc(String tenantId, Collection<String> schoolIds);

    Optional<CrmSchoolDeal> findByTenantIdAndOwnerUserIdAndIdempotencyKey(
            String tenantId, String ownerUserId, String idempotencyKey);

    /** Deals signed at handover approval (signed_at is set there; the stage moves on to active). */
    List<CrmSchoolDeal> findByTenantIdAndOwnerUserIdAndSignedAtIsNotNullOrderBySignedAtDesc(String tenantId, String ownerUserId);

    List<CrmSchoolDeal> findByTenantIdAndOwnerUserIdOrderByCreatedAtDesc(String tenantId, String ownerUserId);

    List<CrmSchoolDeal> findByTenantIdAndOwnerUserIdAndCreatedAtBetween(
            String tenantId, String ownerUserId, OffsetDateTime from, OffsetDateTime to);

    @Query("""
            select
                count(deal.id) as totalCount,
                coalesce(sum(case when deal.signedAt is not null then 1 else 0 end), 0) as signedCount,
                coalesce(sum(case when lower(deal.stage) = 'lost' then 1 else 0 end), 0) as lostCount,
                coalesce(sum(case when deal.signedAt is not null then coalesce(deal.signedRevenue, 0) else 0 end), 0) as signedRevenue,
                coalesce(sum(case when deal.signedAt is not null then 1 else 0 end), 0) as signedDealCount
            from CrmSchoolDeal deal
            where deal.tenantId = :tenantId
              and deal.ownerUserId = :ownerUserId
              and deal.createdAt between :from and :to
            """)
    DealPerformanceAggregate aggregatePerformance(
            @Param("tenantId") String tenantId,
            @Param("ownerUserId") String ownerUserId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    /** The owner's deals in the caller's school scope (schools without an id are open to everyone, as in the access check). */
    @Query("""
            select deal from CrmSchoolDeal deal
            where deal.tenantId = :tenantId and deal.ownerUserId = :ownerUserId
              and (deal.schoolId is null or deal.schoolId = '' or deal.schoolId in :schoolIds)
            order by deal.createdAt desc
            """)
    List<CrmSchoolDeal> findInScopeByOwner(
            @Param("tenantId") String tenantId, @Param("ownerUserId") String ownerUserId,
            @Param("schoolIds") Collection<String> schoolIds);

    @Query("""
            select deal from CrmSchoolDeal deal
            where deal.tenantId = :tenantId and deal.ownerUserId = :ownerUserId and deal.signedAt is not null
              and (deal.schoolId is null or deal.schoolId = '' or deal.schoolId in :schoolIds)
            order by deal.signedAt desc
            """)
    List<CrmSchoolDeal> findSignedInScopeByOwner(
            @Param("tenantId") String tenantId, @Param("ownerUserId") String ownerUserId,
            @Param("schoolIds") Collection<String> schoolIds);

    /** {@link #aggregatePerformance} restricted to the caller's school scope. */
    @Query("""
            select
                count(deal.id) as totalCount,
                coalesce(sum(case when deal.signedAt is not null then 1 else 0 end), 0) as signedCount,
                coalesce(sum(case when lower(deal.stage) = 'lost' then 1 else 0 end), 0) as lostCount,
                coalesce(sum(case when deal.signedAt is not null then coalesce(deal.signedRevenue, 0) else 0 end), 0) as signedRevenue,
                coalesce(sum(case when deal.signedAt is not null then 1 else 0 end), 0) as signedDealCount
            from CrmSchoolDeal deal
            where deal.tenantId = :tenantId
              and deal.ownerUserId = :ownerUserId
              and deal.createdAt between :from and :to
              and (deal.schoolId is null or deal.schoolId = '' or deal.schoolId in :schoolIds)
            """)
    DealPerformanceAggregate aggregatePerformanceInScope(
            @Param("tenantId") String tenantId,
            @Param("ownerUserId") String ownerUserId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("schoolIds") Collection<String> schoolIds
    );

    interface DealPerformanceAggregate {
        Long getTotalCount();
        Long getSignedCount();
        Long getLostCount();
        java.math.BigDecimal getSignedRevenue();
        Long getSignedDealCount();
    }
}
