package com.erg.ergspringboot.repository.calendar;

import com.erg.ergspringboot.model.calendar.CalendarRoom;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface CalendarRoomRepository extends JpaRepository<CalendarRoom, String> {

    Optional<CalendarRoom> findBySchoolIdAndNameIgnoreCase(String schoolId, String name);

    List<CalendarRoom> findByTenantIdAndSchoolIdAndStatus(String tenantId, String schoolId, String status);

    List<CalendarRoom> findByTenantIdAndSchoolIdInAndStatus(String tenantId, List<String> schoolIds, String status);

    List<CalendarRoom> findByTenantIdAndSchoolIdInAndStatus(
            String tenantId, List<String> schoolIds, String status, Pageable pageable);
}
