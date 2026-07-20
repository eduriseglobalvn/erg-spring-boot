package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.AssignmentRecipient;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRecipientRepository extends JpaRepository<AssignmentRecipient, String> {

    List<AssignmentRecipient> findByAssignmentId(String assignmentId);

    List<AssignmentRecipient> findByTenantIdAndStudentId(String tenantId, String studentId);

    Optional<AssignmentRecipient> findByAssignmentIdAndStudentId(String assignmentId, String studentId);

    long countByAssignmentId(String assignmentId);

    void deleteByAssignmentId(String assignmentId);

    void deleteByAssignmentIdAndStudentId(String assignmentId, String studentId);
}
