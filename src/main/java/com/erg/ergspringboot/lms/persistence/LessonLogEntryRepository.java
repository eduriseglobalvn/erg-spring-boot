package com.erg.ergspringboot.lms.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonLogEntryRepository extends JpaRepository<LessonLogEntry, String> {

    List<LessonLogEntry> findByTenantIdAndTeacherUserIdAndLessonDateBetween(
            String tenantId, String teacherUserId, LocalDate from, LocalDate to);

    List<LessonLogEntry> findByTenantIdAndClassIdAndLessonDateBetween(
            String tenantId, String classId, LocalDate from, LocalDate to);

    List<LessonLogEntry> findByTenantIdAndEventIdIn(String tenantId, Collection<String> eventIds);

    Optional<LessonLogEntry> findByTenantIdAndEventIdAndLessonDateAndPeriodNo(
            String tenantId, String eventId, LocalDate lessonDate, Short periodNo);
}
