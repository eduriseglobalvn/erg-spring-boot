package com.erg.ergspringboot.timekeeping.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TkTeachingConfirmationRepository extends JpaRepository<TkTeachingConfirmation, String> {

    Optional<TkTeachingConfirmation> findByTenantIdAndMonthAndTeacherUserId(
            String tenantId, String month, String teacherUserId);

    List<TkTeachingConfirmation> findByTenantIdAndMonth(String tenantId, String month);

    /** Months (yyyy-MM) that still hold a CONFIRMED or ISSUE_REPORTED row to protect against timetable changes. */
    @Query("select distinct c.month from TkTeachingConfirmation c where c.tenantId = :tenantId and c.status in ('CONFIRMED', 'ISSUE_REPORTED')")
    List<String> findMonthsWithConfirmations(@Param("tenantId") String tenantId);
}
