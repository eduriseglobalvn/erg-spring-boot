package com.erg.ergspringboot.calendar.persistence;

import com.erg.ergspringboot.calendar.persistence.CalendarEvent;
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
           "AND (:schoolFiltered = false OR e.schoolId IN :schoolIds) " +
           "AND e.startAt < :to AND e.endAt > :from " +
           "AND e.status IN :statuses " +
           "AND (:teacherFiltered = false OR et.teacherUserId IN :teacherIds) " +
           "AND (:classFiltered = false OR e.academicClassId IN :classIds) " +
           "AND (:subjectFiltered = false OR e.subjectId IN :subjectIds) " +
           "AND (:levelFiltered = false OR e.levelId IN :levelIds) " +
           "ORDER BY e.startAt")
    List<CalendarEvent> findLcmsWorkspaceEvents(
            @Param("tenantId") String tenantId,
            @Param("schoolFiltered") boolean schoolFiltered,
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

    @Query("SELECT DISTINCT et.teacherUserId FROM CalendarEvent e JOIN CalendarEventTeacher et ON e.id = et.eventId " +
           "WHERE e.tenantId = :tenantId AND et.teacherUserId IN :teacherUserIds " +
           "AND e.startAt >= :from AND e.startAt < :to AND e.status IN :statuses")
    List<String> findTeachersWithEventStarting(
            @Param("tenantId") String tenantId,
            @Param("teacherUserIds") Collection<String> teacherUserIds,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("statuses") List<String> statuses);

    /** Published (so neither draft nor cancelled) events of the classes and subjects that start in [from, to). */
    @Query("SELECT e FROM CalendarEvent e WHERE e.tenantId = :tenantId AND e.status = 'PUBLISHED' " +
           "AND e.academicClassId IN :classIds AND e.subjectId IN :subjectIds " +
           "AND e.startAt >= :from AND e.startAt < :to ORDER BY e.startAt")
    List<CalendarEvent> findPublishedForClassesAndSubjects(
            @Param("tenantId") String tenantId,
            @Param("classIds") Collection<String> classIds,
            @Param("subjectIds") Collection<String> subjectIds,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    /** Published events of the tenant that start in [from, to). */
    @Query("SELECT e FROM CalendarEvent e WHERE e.tenantId = :tenantId AND e.status = 'PUBLISHED' " +
           "AND e.startAt >= :from AND e.startAt < :to ORDER BY e.startAt")
    List<CalendarEvent> findPublishedBetween(
            @Param("tenantId") String tenantId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    /** Published events of one class (any subject) that start in [from, to). */
    @Query("SELECT e FROM CalendarEvent e WHERE e.tenantId = :tenantId AND e.status = 'PUBLISHED' " +
           "AND e.academicClassId = :classId AND e.startAt >= :from AND e.startAt < :to ORDER BY e.startAt")
    List<CalendarEvent> findPublishedForClass(
            @Param("tenantId") String tenantId,
            @Param("classId") String classId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

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
    @Query("UPDATE CalendarEvent e SET e.status = :status, e.updatedBy = :updatedBy, e.updatedAt = :updatedAt, e.recordVersion = e.recordVersion + 1 " +
           "WHERE e.tenantId = :tenantId AND e.id IN :ids")
    int updateStatusBatch(
            @Param("tenantId") String tenantId,
            @Param("ids") List<String> ids,
            @Param("status") String status,
            @Param("updatedBy") String updatedBy,
            @Param("updatedAt") OffsetDateTime updatedAt);

    /** The weekly repeats of one grid row: same class, subject, weekday and period of a batch, starting at or after {@code from}. */
    @Query("SELECT e FROM CalendarEvent e WHERE e.tenantId = :tenantId AND e.batchId = :batchId AND e.status = 'PUBLISHED' " +
           "AND e.academicClassId = :classId AND e.subjectId = :subjectId AND e.weekday = :weekday " +
           "AND e.periodStart = :periodStart AND e.startAt >= :from ORDER BY e.startAt")
    List<CalendarEvent> findPublishedSiblings(
            @Param("tenantId") String tenantId,
            @Param("batchId") String batchId,
            @Param("classId") String classId,
            @Param("subjectId") String subjectId,
            @Param("weekday") Short weekday,
            @Param("periodStart") Short periodStart,
            @Param("from") OffsetDateTime from);

    long countByTenantIdAndBatchIdAndStatus(String tenantId, String batchId, String status);

    /** Moves every event of a batch still in {@code fromStatus}, so a concurrent cancel is never undone (no id list). */
    @Modifying
    @Query("UPDATE CalendarEvent e SET e.status = :status, e.updatedBy = :updatedBy, e.updatedAt = :updatedAt, e.recordVersion = e.recordVersion + 1 " +
           "WHERE e.tenantId = :tenantId AND e.batchId = :batchId AND e.status = :fromStatus")
    int updateStatusByBatchFromStatus(
            @Param("tenantId") String tenantId,
            @Param("batchId") String batchId,
            @Param("fromStatus") String fromStatus,
            @Param("status") String status,
            @Param("updatedBy") String updatedBy,
            @Param("updatedAt") OffsetDateTime updatedAt);

    /** Cancels the events of a batch that are not cancelled yet; with {@code startsAfter} only periods not yet started. */
    @Modifying
    @Query("UPDATE CalendarEvent e SET e.status = 'CANCELLED', e.updatedBy = :updatedBy, e.updatedAt = :updatedAt, e.recordVersion = e.recordVersion + 1 " +
           "WHERE e.tenantId = :tenantId AND e.batchId = :batchId AND e.status <> 'CANCELLED'")
    int cancelAllByBatch(
            @Param("tenantId") String tenantId,
            @Param("batchId") String batchId,
            @Param("updatedBy") String updatedBy,
            @Param("updatedAt") OffsetDateTime updatedAt);

    @Modifying
    @Query("UPDATE CalendarEvent e SET e.status = 'CANCELLED', e.updatedBy = :updatedBy, e.updatedAt = :updatedAt, e.recordVersion = e.recordVersion + 1 " +
           "WHERE e.tenantId = :tenantId AND e.batchId = :batchId AND e.status <> 'CANCELLED' AND e.startAt > :startsAfter")
    int cancelFutureByBatch(
            @Param("tenantId") String tenantId,
            @Param("batchId") String batchId,
            @Param("startsAfter") OffsetDateTime startsAfter,
            @Param("updatedBy") String updatedBy,
            @Param("updatedAt") OffsetDateTime updatedAt);

    /** Drops the teacher links of a batch's draft events before the drafts are replaced. */
    @Modifying
    @Query("DELETE FROM CalendarEventTeacher t WHERE t.tenantId = :tenantId AND t.eventId IN "
            + "(SELECT e.id FROM CalendarEvent e WHERE e.tenantId = :tenantId AND e.batchId = :batchId AND e.status = 'DRAFT')")
    int deleteDraftTeachersByBatch(@Param("tenantId") String tenantId, @Param("batchId") String batchId);

    @Modifying
    @Query("DELETE FROM CalendarEvent e WHERE e.tenantId = :tenantId AND e.batchId = :batchId AND e.status = 'DRAFT'")
    int deleteDraftsByBatch(@Param("tenantId") String tenantId, @Param("batchId") String batchId);
}
