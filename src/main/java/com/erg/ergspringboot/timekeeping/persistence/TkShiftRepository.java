package com.erg.ergspringboot.timekeeping.persistence;

import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TkShiftRepository extends JpaRepository<TkShift, String> {

    List<TkShift> findByTenantIdAndSchoolIdOrderByStartTimeAsc(String tenantId, String schoolId);

    boolean existsByTenantIdAndSchoolId(String tenantId, String schoolId);

    /** Shifts of every tenant running on ISO weekday {@code weekday} whose end is in ({@code after}, {@code upTo}]. */
    @Query(value = """
            select * from tk_shifts
            where :weekday = any(weekdays) and end_time > :after and end_time <= :upTo
            order by tenant_id, id
            """, nativeQuery = true)
    List<TkShift> findEndingBetween(
            @Param("weekday") int weekday, @Param("after") LocalTime after, @Param("upTo") LocalTime upTo);
}
