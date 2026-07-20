package com.erg.ergspringboot.repository.calendar;

import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.repository.calendar.projection.CalendarTeacherColorProjection;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarEventTeacherRepository extends JpaRepository<CalendarEventTeacher, String> {

    List<CalendarEventTeacher> findByTenantIdAndTeacherUserId(String tenantId, String teacherUserId);

    List<CalendarEventTeacher> findByEventIdIn(List<String> eventIds);

    List<CalendarEventTeacher> findByTenantIdAndEventIdIn(String tenantId, Collection<String> eventIds);

    @Query("SELECT new com.erg.ergspringboot.repository.calendar.projection.CalendarTeacherColorProjection(" +
           "et.teacherUserId, et.colorKey) " +
           "FROM CalendarEventTeacher et JOIN CalendarEvent e ON e.id = et.eventId " +
           "WHERE e.tenantId = :tenantId AND et.tenantId = :tenantId " +
           "AND e.schoolId IN :schoolIds " +
           "AND e.startAt < :to AND e.endAt > :from " +
           "AND e.status IN :statuses " +
           "ORDER BY e.startAt")
    List<CalendarTeacherColorProjection> findTeacherColorKeysInRange(
            @Param("tenantId") String tenantId,
            @Param("schoolIds") List<String> schoolIds,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("statuses") List<String> statuses);

    @Query("SELECT et FROM CalendarEventTeacher et JOIN CalendarEvent e ON e.id = et.eventId " +
           "WHERE e.tenantId = :tenantId AND et.tenantId = :tenantId " +
           "AND et.teacherUserId IN :teacherIds " +
           "AND e.startAt < :to AND e.endAt > :from " +
           "AND e.status <> :excludedStatus")
    List<CalendarEventTeacher> findTeacherAssignmentsInWindow(
            @Param("tenantId") String tenantId,
            @Param("teacherIds") Collection<String> teacherIds,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("excludedStatus") String excludedStatus);

    @Query("SELECT et.eventId FROM CalendarEventTeacher et JOIN CalendarEvent e ON e.id = et.eventId " +
           "WHERE e.tenantId = :tenantId AND et.teacherUserId = :teacherUserId " +
           "AND e.schoolId IN :schoolIds AND e.status = :status " +
           "AND e.startAt >= :from AND e.startAt < :to")
    List<String> findEventIdsByTeacher(@Param("tenantId") String tenantId,
                                       @Param("teacherUserId") String teacherUserId,
                                       @Param("schoolIds") List<String> schoolIds,
                                       @Param("from") OffsetDateTime from,
                                       @Param("to") OffsetDateTime to,
                                       @Param("status") String status);
}
