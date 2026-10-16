package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.StudentEnrollment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, String> {
    Optional<StudentEnrollment> findByTenantIdAndId(String tenantId, String id);
    Optional<StudentEnrollment> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
    Optional<StudentEnrollment> findByTenantIdAndStudentIdAndClassIdAndAcademicYear(
            String tenantId, String studentId, String classId, String academicYear);
    List<StudentEnrollment> findByTenantIdAndStudentIdAndStatusIgnoreCaseOrderByCreatedAtAsc(
            String tenantId, String studentId, String status);
}
