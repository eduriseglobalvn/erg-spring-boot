package com.erg.ergspringboot.repository.school;

import com.erg.ergspringboot.model.school.SchoolAcademicYear;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolAcademicYearRepository extends JpaRepository<SchoolAcademicYear, String> {
    List<SchoolAcademicYear> findByTenantIdAndSchoolIdOrderByStartYearDesc(String tenantId, String schoolId);
    Optional<SchoolAcademicYear> findByIdAndTenantIdAndSchoolId(String id, String tenantId, String schoolId);
    Optional<SchoolAcademicYear> findFirstByTenantIdAndSchoolIdAndStatus(String tenantId, String schoolId, String status);
    boolean existsByTenantIdAndSchoolIdAndStartYear(String tenantId, String schoolId, int startYear);
}
