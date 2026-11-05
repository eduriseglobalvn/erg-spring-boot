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

    /** Class ids of this school and tenant among {@code ids} (native: classes belong to the LMS module). */
    @Query(value = "select id from academic_classes where tenant_id = :tenantId and school_id = :schoolId and id in (:ids)",
            nativeQuery = true)
    List<String> existingClassIds(@Param("tenantId") String tenantId, @Param("schoolId") String schoolId,
                                  @Param("ids") Collection<String> ids);

    /** Subject ids of this tenant among {@code ids} (native: school subjects belong to the LMS module). */
    @Query(value = "select id from lms_subjects where tenant_id = :tenantId and id in (:ids)", nativeQuery = true)
    List<String> existingSubjectIds(@Param("tenantId") String tenantId, @Param("ids") Collection<String> ids);
}
