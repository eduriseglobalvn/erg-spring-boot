package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.Attempt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptRepository extends JpaRepository<Attempt, String> {

    Optional<Attempt> findFirstByTenantIdAndAssignmentIdAndQuizIdAndStudentIdAndStatusIgnoreCase(
            String tenantId,
            String assignmentId,
            String quizId,
            String studentId,
            String status
    );

    List<Attempt> findByTenantIdAndAssignmentIdOrderByUpdatedAtDesc(String tenantId, String assignmentId);

    List<Attempt> findByTenantIdAndQuizIdOrderByUpdatedAtDesc(String tenantId, String quizId);

    List<Attempt> findTop3ByTenantIdAndAssignmentIdAndStudentIdOrderByUpdatedAtDesc(
            String tenantId,
            String assignmentId,
            String studentId
    );
}
