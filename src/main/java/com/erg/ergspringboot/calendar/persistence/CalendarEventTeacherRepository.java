package com.erg.ergspringboot.calendar.persistence;

import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacher;
import com.erg.ergspringboot.calendar.persistence.projection.CalendarTeacherColorProjection;
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

    /** One row per (teacher, colour key), the earliest-starting first: the catalog keeps the first colour of each teacher. */
    @Query("SELECT new com.erg.ergspringboot.calendar.persistence.projection.CalendarTeacherColorProjection(" +
           "et.teacherUserId, et.colorKey) " +
           "FROM CalendarEventTeacher et JOIN CalendarEvent e ON e.id = et.eventId " +
           "WHERE e.tenantId = :tenantId AND et.tenantId = :tenantId " +
           "AND e.schoolId IN :schoolIds " +
           "AND e.startAt < :to AND e.endAt > :from " +
           "AND e.status IN :statuses " +
           "GROUP BY et.teacherUserId, et.colorKey ORDER BY MIN(e.startAt)")
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

    @Query("SELECT COUNT(et) FROM CalendarEventTeacher et JOIN CalendarEvent e ON e.id = et.eventId " +
           "WHERE e.tenantId = :tenantId AND et.tenantId = :tenantId AND e.batchId = :batchId")
    long countByBatch(@Param("tenantId") String tenantId, @Param("batchId") String batchId);

    /** Teachers on the periods of one batch (any status), for the principal-cache eviction after a timetable change. */
    @Query("SELECT DISTINCT et.teacherUserId FROM CalendarEventTeacher et JOIN CalendarEvent e ON e.id = et.eventId " +
           "WHERE e.tenantId = :tenantId AND et.tenantId = :tenantId AND e.batchId = :batchId")
    List<String> findTeacherIdsByBatch(@Param("tenantId") String tenantId, @Param("batchId") String batchId);

    @Query("SELECT DISTINCT et.teacherUserId FROM CalendarEventTeacher et " +
           "WHERE et.tenantId = :tenantId AND et.eventId IN :eventIds")
    List<String> findTeacherIdsByEventIds(@Param("tenantId") String tenantId, @Param("eventIds") Collection<String> eventIds);
}
