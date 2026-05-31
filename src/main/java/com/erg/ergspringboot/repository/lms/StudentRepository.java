package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.Student;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, String> {

    List<Student> findByTenantIdAndSchoolIdOrderByFullNameAsc(String tenantId, String schoolId);

    List<Student> findByTenantIdAndAcademicClassIdOrderByFullNameAsc(String tenantId, String academicClassId);

    Optional<Student> findFirstByTenantIdAndAuthUserId(String tenantId, String authUserId);
}
