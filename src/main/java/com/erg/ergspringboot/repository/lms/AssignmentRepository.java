package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.Assignment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository extends JpaRepository<Assignment, String> {

    List<Assignment> findByTenantIdAndAssignedByOrderByCreatedAtDesc(String tenantId, String assignedBy);

    List<Assignment> findByTenantIdAndAcademicClassIdOrderByCreatedAtDesc(String tenantId, String academicClassId);

    List<Assignment> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
