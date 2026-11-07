package com.erg.ergspringboot.timekeeping.persistence;

import com.erg.ergspringboot.timekeeping.RequestStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TkRequestRepository extends JpaRepository<TkRequest, String> {

    long countByTenantIdAndUserIdAndStatus(String tenantId, String userId, RequestStatus status);

    /** Approved ANNUAL leave overlapping {@code from..to} (a leave across year-end is split by the caller). */
    @Query("""
            select r from TkRequest r
            where r.tenantId = :tenantId and r.userId = :userId
              and r.type = com.erg.ergspringboot.timekeeping.RequestType.LEAVE
              and r.leaveKind = com.erg.ergspringboot.timekeeping.LeaveKind.ANNUAL
              and r.status = com.erg.ergspringboot.timekeeping.RequestStatus.APPROVED
              and r.fromDate <= :to and r.toDate >= :from
            """)
    List<TkRequest> findApprovedAnnualLeaveOverlapping(@Param("tenantId") String tenantId,
            @Param("userId") String userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Approved requests that shape a day: LEAVE / BUSINESS covering it, EXPLANATION for it. */
    @Query("""
            select r from TkRequest r
            where r.tenantId = :tenantId
              and r.status = com.erg.ergspringboot.timekeeping.RequestStatus.APPROVED
              and ((r.type in (com.erg.ergspringboot.timekeeping.RequestType.LEAVE,
                               com.erg.ergspringboot.timekeeping.RequestType.BUSINESS)
                    and r.fromDate <= :date and r.toDate >= :date)
                or (r.type = com.erg.ergspringboot.timekeeping.RequestType.EXPLANATION and r.workDate = :date))
            order by r.userId, r.submittedAt, r.id
            """)
    List<TkRequest> findApprovedOn(@Param("tenantId") String tenantId, @Param("date") LocalDate date);

    @Query("""
            select r from TkRequest r
            where r.tenantId = :tenantId and r.userId = :userId
              and r.status = com.erg.ergspringboot.timekeeping.RequestStatus.APPROVED
              and ((r.type in (com.erg.ergspringboot.timekeeping.RequestType.LEAVE,
                               com.erg.ergspringboot.timekeeping.RequestType.BUSINESS)
                    and r.fromDate <= :date and r.toDate >= :date)
                or (r.type = com.erg.ergspringboot.timekeeping.RequestType.EXPLANATION and r.workDate = :date))
            order by r.submittedAt, r.id
            """)
    List<TkRequest> findApprovedOnForUser(@Param("tenantId") String tenantId, @Param("userId") String userId,
            @Param("date") LocalDate date);

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

    /** Serialises approve/reject/remind on one request (row lock until commit). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TkRequest r where r.tenantId = :tenantId and r.id = :id")
    Optional<TkRequest> findForUpdate(@Param("tenantId") String tenantId, @Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TkRequest r where r.tenantId = :tenantId and r.userId = :userId and r.id = :id")
    Optional<TkRequest> findOwnForUpdate(@Param("tenantId") String tenantId, @Param("userId") String userId,
            @Param("id") String id);

    Optional<TkRequest> findByTenantIdAndUserIdAndId(String tenantId, String userId, String id);

    Page<TkRequest> findByTenantIdAndUserId(String tenantId, String userId, Pageable pageable);

    Page<TkRequest> findByTenantIdAndUserIdAndStatus(String tenantId, String userId, RequestStatus status, Pageable pageable);

    /** ANNUAL leave in {@code status} overlapping {@code from..to}. */
    @Query("""
            select r from TkRequest r
            where r.tenantId = :tenantId and r.userId = :userId
              and r.type = com.erg.ergspringboot.timekeeping.RequestType.LEAVE
              and r.leaveKind = com.erg.ergspringboot.timekeeping.LeaveKind.ANNUAL
              and r.status = :status
              and r.fromDate <= :to and r.toDate >= :from
            """)
    List<TkRequest> findAnnualLeaveOverlapping(@Param("tenantId") String tenantId, @Param("userId") String userId,
            @Param("status") RequestStatus status, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** PENDING/APPROVED requests of the user touching {@code from..to} (ranges, or an explanation day). */
    @Query("""
            select r from TkRequest r
            where r.tenantId = :tenantId and r.userId = :userId and r.id <> :excludeId
              and r.status in (com.erg.ergspringboot.timekeeping.RequestStatus.PENDING,
                               com.erg.ergspringboot.timekeeping.RequestStatus.APPROVED)
              and ((r.fromDate <= :to and r.toDate >= :from) or (r.workDate between :from and :to))
            """)
    List<TkRequest> findActiveTouching(@Param("tenantId") String tenantId, @Param("userId") String userId,
            @Param("excludeId") String excludeId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
