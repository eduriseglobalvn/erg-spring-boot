package com.erg.ergspringboot.timekeeping.persistence;

import java.util.Optional;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TkHolidayRepository extends JpaRepository<TkHoliday, String> {

    /** Tenant-wide holidays (no school) plus the holidays of this school. */
    List<TkHoliday> findByTenantIdAndHolidayDate(String tenantId, LocalDate holidayDate);

    @Query("""
            select h from TkHoliday h
            where h.tenantId = :tenantId and (h.schoolId is null or h.schoolId = :schoolId)
              and h.holidayDate between :from and :to
            """)
    List<TkHoliday> findForSchool(@Param("tenantId") String tenantId, @Param("schoolId") String schoolId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    Optional<TkHoliday> findByIdAndTenantId(String id, String tenantId);

    List<TkHoliday> findByTenantIdAndHolidayDateBetweenOrderByHolidayDateAsc(String tenantId, LocalDate from, LocalDate to);

    /** A null {@code schoolId} matches the tenant-wide row. */
    boolean existsByTenantIdAndSchoolIdAndHolidayDate(String tenantId, String schoolId, LocalDate date);
}
