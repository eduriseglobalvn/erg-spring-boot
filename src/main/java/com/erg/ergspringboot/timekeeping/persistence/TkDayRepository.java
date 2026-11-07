package com.erg.ergspringboot.timekeeping.persistence;

import com.erg.ergspringboot.timekeeping.DayStatus;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TkDayRepository extends JpaRepository<TkDay, String> {

    Optional<TkDay> findByTenantIdAndUserIdAndWorkDate(String tenantId, String userId, LocalDate workDate);

    List<TkDay> findByTenantIdAndWorkDate(String tenantId, LocalDate workDate);

    List<TkDay> findByTenantIdAndUserIdAndWorkDateBetweenOrderByWorkDateAsc(
            String tenantId, String userId, LocalDate from, LocalDate to);

    boolean existsByTenantIdAndShiftId(String tenantId, String shiftId);

    List<TkDay> findByTenantIdAndSchoolIdAndUserIdInAndWorkDateBetween(
            String tenantId, String schoolId, Collection<String> userIds, LocalDate from, LocalDate to);

    @Query("""
            select distinct d.userId from TkDay d
            where d.tenantId = :tenantId and d.schoolId = :schoolId and d.workDate between :from and :to
            """)
    List<String> findUserIdsWithRows(@Param("tenantId") String tenantId, @Param("schoolId") String schoolId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    long countByTenantIdAndSchoolIdAndStatusAndWorkDateBetween(
            String tenantId, String schoolId, DayStatus status, LocalDate from, LocalDate to);

    long countByTenantIdAndUserIdAndLateMinutesGreaterThanAndWorkDateBetween(
            String tenantId, String userId, int minutes, LocalDate from, LocalDate to);
}
