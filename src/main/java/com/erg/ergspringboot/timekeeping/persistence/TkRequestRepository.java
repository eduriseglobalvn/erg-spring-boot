package com.erg.ergspringboot.timekeeping.persistence;

import com.erg.ergspringboot.timekeeping.RequestStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TkRequestRepository extends JpaRepository<TkRequest, String> {

    long countByTenantIdAndUserIdAndStatus(String tenantId, String userId, RequestStatus status);

    @Query("""
            select coalesce(sum(r.days), 0) from TkRequest r
            where r.tenantId = :tenantId and r.userId = :userId
              and r.type = com.erg.ergspringboot.timekeeping.RequestType.LEAVE
              and r.leaveKind = com.erg.ergspringboot.timekeeping.LeaveKind.ANNUAL
              and r.status = com.erg.ergspringboot.timekeeping.RequestStatus.APPROVED
              and r.fromDate between :from and :to
            """)
    BigDecimal sumApprovedAnnualLeaveDays(@Param("tenantId") String tenantId, @Param("userId") String userId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Explanations that use up the monthly allowance: pending or approved, for a day in the range. */
    @Query("""
            select count(r) from TkRequest r
            where r.tenantId = :tenantId and r.userId = :userId
              and r.type = com.erg.ergspringboot.timekeeping.RequestType.EXPLANATION
              and r.status in (com.erg.ergspringboot.timekeeping.RequestStatus.PENDING,
                               com.erg.ergspringboot.timekeeping.RequestStatus.APPROVED)
              and r.workDate between :from and :to
            """)
    long countActiveExplanations(@Param("tenantId") String tenantId, @Param("userId") String userId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select count(r) > 0 from TkRequest r
            where r.tenantId = :tenantId and r.userId = :userId
              and r.type = com.erg.ergspringboot.timekeeping.RequestType.WFH
              and r.status = com.erg.ergspringboot.timekeeping.RequestStatus.APPROVED
              and r.fromDate <= :date and r.toDate >= :date
            """)
    boolean hasApprovedWfh(@Param("tenantId") String tenantId, @Param("userId") String userId,
            @Param("date") LocalDate date);
}
