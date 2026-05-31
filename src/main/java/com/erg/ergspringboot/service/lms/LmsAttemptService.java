package com.erg.ergspringboot.service.lms;

import com.erg.ergspringboot.model.lms.Attempt;
import com.erg.ergspringboot.repository.lms.AttemptRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class LmsAttemptService {

    private final AttemptRepository repository;

    public LmsAttemptService(AttemptRepository repository) {
        this.repository = repository;
    }

    public Optional<Attempt> findById(String id) {
        return repository.findById(id);
    }

    public Optional<Attempt> findInProgress(String tenantId, String assignmentId, String quizId, String studentId) {
        return repository.findFirstByTenantIdAndAssignmentIdAndQuizIdAndStudentIdAndStatusIgnoreCase(
                tenantId, assignmentId, quizId, studentId, "in_progress");
    }

    public List<Attempt> findTop3ByAssignmentAndStudent(String tenantId, String assignmentId, String studentId) {
        return repository.findTop3ByTenantIdAndAssignmentIdAndStudentIdOrderByUpdatedAtDesc(tenantId, assignmentId, studentId);
    }

    public List<Attempt> findByAssignment(String tenantId, String assignmentId) {
        return repository.findByTenantIdAndAssignmentIdOrderByUpdatedAtDesc(tenantId, assignmentId);
    }

    public List<Attempt> findByQuiz(String tenantId, String quizId) {
        return repository.findByTenantIdAndQuizIdOrderByUpdatedAtDesc(tenantId, quizId);
    }

    @Transactional("postgresTransactionManager")
    public Attempt save(Attempt attempt) {
        return repository.save(attempt);
    }
}
