package com.erg.ergspringboot.repository.calendar;

import com.erg.ergspringboot.model.calendar.CalendarEvent;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, String> {

    List<CalendarEvent> findByBatchIdIn(List<String> batchIds);

    List<CalendarEvent> findByTenantIdAndBatchIdIn(String tenantId, Collection<String> batchIds);

    List<CalendarEvent> findByTenantIdAndBatchId(String tenantId, String batchId);

    Optional<CalendarEvent> findByTenantIdAndId(String tenantId, String id);

    List<CalendarEvent> findByTenantIdAndIdIn(String tenantId, Collection<String> ids);

    long countByTenantIdAndBatchId(String tenantId, String batchId);

    List<CalendarEvent> findByTenantIdAndSchoolIdInAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(
            String tenantId, List<String> schoolIds, OffsetDateTime to, OffsetDateTime from, List<String> statuses, Sort sort);

    @Query("SELECT DISTINCT e FROM CalendarEvent e LEFT JOIN CalendarEventTeacher et ON e.id = et.eventId " +
           "WHERE e.tenantId = :tenantId " +
           "AND e.schoolId IN :schoolIds " +
           "AND e.startAt < :to AND e.endAt > :from " +
           "AND e.status IN :statuses " +
           "AND (:teacherFiltered = false OR et.teacherUserId IN :teacherIds) " +
           "AND (:classFiltered = false OR e.academicClassId IN :classIds) " +
           "AND (:subjectFiltered = false OR e.subjectId IN :subjectIds) " +
           "AND (:levelFiltered = false OR e.levelId IN :levelIds) " +
           "ORDER BY e.startAt")
    List<CalendarEvent> findLcmsWorkspaceEvents(
            @Param("tenantId") String tenantId,
            @Param("schoolIds") Collection<String> schoolIds,
            @Param("to") OffsetDateTime to,
            @Param("from") OffsetDateTime from,
            @Param("statuses") Collection<String> statuses,
            @Param("teacherFiltered") boolean teacherFiltered,
            @Param("teacherIds") Collection<String> teacherIds,
            @Param("classFiltered") boolean classFiltered,
            @Param("classIds") Collection<String> classIds,
            @Param("subjectFiltered") boolean subjectFiltered,
            @Param("subjectIds") Collection<String> subjectIds,
            @Param("levelFiltered") boolean levelFiltered,
            @Param("levelIds") Collection<String> levelIds);

    @Query("SELECT e FROM CalendarEvent e JOIN CalendarEventTeacher et ON e.id = et.eventId " +
           "WHERE e.tenantId = :tenantId AND et.teacherUserId = :teacherUserId " +
           "AND e.startAt < :to AND e.endAt > :from AND e.status IN :statuses " +
           "ORDER BY e.startAt")
    List<CalendarEvent> findTeacherCalendarEvents(
            @Param("tenantId") String tenantId,
            @Param("teacherUserId") String teacherUserId,
            @Param("to") OffsetDateTime to,
            @Param("from") OffsetDateTime from,
            @Param("statuses") List<String> statuses);

    @Query("SELECT DISTINCT e FROM CalendarEvent e JOIN CalendarEventTeacher et ON e.id = et.eventId " +
           "WHERE e.tenantId = :tenantId AND et.teacherUserId = :teacherUserId " +
           "AND e.startAt < :to AND e.endAt > :from AND e.status IN :statuses " +
           "AND (:schoolFiltered = false OR e.schoolId IN :schoolIds) " +
           "AND (:classFiltered = false OR e.academicClassId IN :classIds) " +
           "AND (:subjectFiltered = false OR e.subjectId IN :subjectIds) " +
           "AND (:levelFiltered = false OR e.levelId IN :levelIds) " +
           "ORDER BY e.startAt")
    List<CalendarEvent> findTeacherCalendarEventsFiltered(
            @Param("tenantId") String tenantId,
            @Param("teacherUserId") String teacherUserId,
            @Param("to") OffsetDateTime to,
            @Param("from") OffsetDateTime from,
            @Param("statuses") Collection<String> statuses,
            @Param("schoolFiltered") boolean schoolFiltered,
            @Param("schoolIds") Collection<String> schoolIds,
            @Param("classFiltered") boolean classFiltered,
            @Param("classIds") Collection<String> classIds,
            @Param("subjectFiltered") boolean subjectFiltered,
            @Param("subjectIds") Collection<String> subjectIds,
            @Param("levelFiltered") boolean levelFiltered,
            @Param("levelIds") Collection<String> levelIds);

    List<CalendarEvent> findByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(
            String tenantId, String academicClassId, OffsetDateTime to, OffsetDateTime from, List<String> statuses, Sort sort);

    List<CalendarEvent> findByTenantIdAndIdInAndStatusNot(
            String tenantId, Collection<String> ids, String excludedStatus);

    @Query("SELECT e FROM CalendarEvent e " +
           "WHERE e.tenantId = :tenantId " +
           "AND e.academicClassId = :academicClassId " +
           "AND e.startAt < :to AND e.endAt > :from " +
           "AND e.status IN :statuses " +
           "AND (:subjectFiltered = false OR e.subjectId IN :subjectIds) " +
           "AND (:levelFiltered = false OR e.levelId IN :levelIds) " +
           "ORDER BY e.startAt")
    List<CalendarEvent> findStudentCalendarEventsFiltered(
            @Param("tenantId") String tenantId,
            @Param("academicClassId") String academicClassId,
            @Param("to") OffsetDateTime to,
            @Param("from") OffsetDateTime from,
            @Param("statuses") Collection<String> statuses,
            @Param("subjectFiltered") boolean subjectFiltered,
            @Param("subjectIds") Collection<String> subjectIds,
            @Param("levelFiltered") boolean levelFiltered,
            @Param("levelIds") Collection<String> levelIds);

    @Query("SELECT e FROM CalendarEvent e " +
           "WHERE e.tenantId = :tenantId " +
           "AND e.academicClassId IN :classIds " +
           "AND e.startAt < :to AND e.endAt > :from " +
           "AND e.status <> :excludedStatus")
    List<CalendarEvent> findClassTimeConflictsInWindow(
            @Param("tenantId") String tenantId,
            @Param("classIds") Collection<String> classIds,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("excludedStatus") String excludedStatus);

    @Query("SELECT e FROM CalendarEvent e " +
           "WHERE e.tenantId = :tenantId " +
           "AND e.schoolId IN :schoolIds " +
           "AND e.roomName IS NOT NULL " +
           "AND e.startAt < :to AND e.endAt > :from " +
           "AND e.status <> :excludedStatus")
    List<CalendarEvent> findRoomTimeConflictsInWindow(
            @Param("tenantId") String tenantId,
            @Param("schoolIds") Collection<String> schoolIds,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("excludedStatus") String excludedStatus);

    @Query("SELECT e FROM CalendarEvent e JOIN CalendarEventTeacher et ON e.id = et.eventId " +
           "WHERE e.tenantId = :tenantId AND et.teacherUserId = :teacherUserId " +
           "AND e.startAt < :endAt AND e.endAt > :startAt AND e.status <> 'CANCELLED'")
    List<CalendarEvent> findTeacherTimeConflicts(
            @Param("tenantId") String tenantId,
            @Param("teacherUserId") String teacherUserId,
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt);

    long countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
            String tenantId, String classId, OffsetDateTime endAt, OffsetDateTime startAt, String status);

    long countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
            String tenantId, String schoolId, String roomName, OffsetDateTime endAt, OffsetDateTime startAt, String status);

    @Modifying
    @Query("UPDATE CalendarEvent e SET e.status = :status, e.updatedBy = :updatedBy, e.updatedAt = :updatedAt " +
           "WHERE e.tenantId = :tenantId AND e.id IN :ids")
    int updateStatusBatch(
            @Param("tenantId") String tenantId,
            @Param("ids") List<String> ids,
            @Param("status") String status,
            @Param("updatedBy") String updatedBy,
            @Param("updatedAt") OffsetDateTime updatedAt);
}
