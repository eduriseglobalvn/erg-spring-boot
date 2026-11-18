package com.erg.ergspringboot.curriculum.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CurriculumClassSubjectAllocationRepository extends JpaRepository<CurriculumClassSubjectAllocation, String> {

    List<CurriculumClassSubjectAllocation> findByTenantIdAndSchoolIdAndAcademicYearOrderByAcademicClassIdAscSubjectIdAsc(
            String tenantId, String schoolId, String academicYear);

    /** Bulk delete runs immediately, so the replacement inserts never meet the unique constraint. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CurriculumClassSubjectAllocation a "
            + "where a.tenantId = :tenantId and a.schoolId = :schoolId and a.academicYear = :academicYear")
    int deleteScope(@Param("tenantId") String tenantId, @Param("schoolId") String schoolId,
                    @Param("academicYear") String academicYear);

    /** Latest timetable lesson (not cancelled) or class-log entry of the subject in the class; null when there is none. */
    @Query(value = """
            select max(d) from (
                select max((start_at at time zone 'Asia/Ho_Chi_Minh')::date) d from teaching_schedule_events
                where tenant_id = :tenantId and academic_class_id = :classId and subject_id = :subjectId
                  and upper(coalesce(status, '')) not like 'CANCEL%'
                union all
                select max(lesson_date) from lms_lesson_log_entries
                where tenant_id = :tenantId and class_id = :classId and subject_id = :subjectId) x
            """, nativeQuery = true)
    java.time.LocalDate lastTeachingDate(@Param("tenantId") String tenantId, @Param("classId") String classId,
                                         @Param("subjectId") String subjectId);

    /** Latest month ("yyyy-MM") with teaching-hour lines of the subject at the school; null when there is none. */
    @Query(value = "select max(month) from tk_teaching_month_lines where tenant_id = :tenantId and school_id = :schoolId and subject_id = :subjectId",
            nativeQuery = true)
    String lastTeachingHoursMonth(@Param("tenantId") String tenantId, @Param("schoolId") String schoolId,
                                  @Param("subjectId") String subjectId);

    /** Class ids of this school and tenant among {@code ids} (native: classes belong to the LMS module). */
    @Query(value = "select id from academic_classes where tenant_id = :tenantId and school_id = :schoolId and id in (:ids)",
            nativeQuery = true)
    List<String> existingClassIds(@Param("tenantId") String tenantId, @Param("schoolId") String schoolId,
                                  @Param("ids") Collection<String> ids);

    List<CurriculumClassSubjectAllocation> findByTenantIdAndSchoolIdOrderByAcademicClassIdAscSubjectIdAsc(
            String tenantId, String schoolId);

    /** "levelId|subjectId" of this tenant's levels among {@code ids}. */
    @Query(value = "select id || '|' || subject_id from curriculum_levels where tenant_id = :tenantId and id in (:ids)",
            nativeQuery = true)
    List<String> existingLevelKeys(@Param("tenantId") String tenantId, @Param("ids") Collection<String> ids);

    /** Subject ids of this tenant among {@code ids}. */
    @Query(value = "select id from curriculum_subjects where tenant_id = :tenantId and id in (:ids)", nativeQuery = true)
    List<String> existingSubjectIds(@Param("tenantId") String tenantId, @Param("ids") Collection<String> ids);
}
