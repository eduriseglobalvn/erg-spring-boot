package com.erg.ergspringboot.repository.crm;

import com.erg.ergspringboot.model.crm.CrmSchoolDeal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrmSchoolDealRepository extends JpaRepository<CrmSchoolDeal, String> {

    List<CrmSchoolDeal> findBySchoolIdInOrderByCreatedAtDesc(Collection<String> schoolIds);

    Optional<CrmSchoolDeal> findByOwnerUserIdAndIdempotencyKey(String ownerUserId, String idempotencyKey);

    List<CrmSchoolDeal> findByOwnerUserIdAndStageIgnoreCaseOrderBySignedAtDesc(String ownerUserId, String stage);

    List<CrmSchoolDeal> findByOwnerUserIdAndCreatedAtBetween(String ownerUserId, OffsetDateTime from, OffsetDateTime to);

    @Query("""
            select
                count(deal.id) as totalCount,
                coalesce(sum(case when lower(deal.stage) = 'signed' then 1 else 0 end), 0) as signedCount,
                coalesce(sum(case when lower(deal.stage) = 'lost' then 1 else 0 end), 0) as lostCount,
                coalesce(sum(case when lower(deal.stage) = 'signed' then coalesce(deal.signedRevenue, 0) else 0 end), 0) as signedRevenue,
                coalesce(sum(case when lower(deal.stage) = 'signed' then 1 else 0 end), 0) as signedDealCount
            from CrmSchoolDeal deal
            where deal.ownerUserId = :ownerUserId
              and deal.createdAt between :from and :to
            """)
    DealPerformanceAggregate aggregatePerformance(
            @Param("ownerUserId") String ownerUserId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    interface DealPerformanceAggregate {
        Long getTotalCount();
        Long getSignedCount();
        Long getLostCount();
        java.math.BigDecimal getSignedRevenue();
        Long getSignedDealCount();
    }
}
