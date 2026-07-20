package com.erg.ergspringboot.repository.school;

import com.erg.ergspringboot.model.school.SchoolGrade;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolGradeRepository extends JpaRepository<SchoolGrade, String> {
    List<SchoolGrade> findByTenantIdAndSchoolIdAndAcademicYearIdOrderBySortOrderAsc(
            String tenantId, String schoolId, String academicYearId);
    Optional<SchoolGrade> findByTenantIdAndSchoolIdAndAcademicYearIdAndGradeCode(
            String tenantId, String schoolId, String academicYearId, String gradeCode);
    long countByTenantIdAndSchoolIdAndAcademicYearId(String tenantId, String schoolId, String academicYearId);
}
