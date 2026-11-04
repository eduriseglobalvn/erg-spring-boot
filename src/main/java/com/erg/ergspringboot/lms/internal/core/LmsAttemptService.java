package com.erg.ergspringboot.lms.internal.core;

import com.erg.ergspringboot.lms.LmsStatePolicy;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.lms.persistence.AttemptRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public Optional<Attempt> findByTenantIdAndId(String tenantId, String id) {
        return repository.findByTenantIdAndId(tenantId, id);
    }

    public Optional<Attempt> findInProgress(String tenantId, String assignmentId, String quizId, String studentId) {
        return repository.findFirstByTenantIdAndAssignmentIdAndQuizIdAndStudentIdAndStatusIgnoreCase(
                tenantId, assignmentId, quizId, studentId, "in_progress");
    }

    public Optional<Attempt> findByStartIdempotencyKey(
            String tenantId,
            String assignmentId,
            String quizId,
            String studentId,
            String startIdempotencyKey
    ) {
        if (startIdempotencyKey == null || startIdempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return repository.findFirstByTenantIdAndAssignmentIdAndQuizIdAndStudentIdAndStartIdempotencyKey(
                tenantId, assignmentId, quizId, studentId, startIdempotencyKey.trim());
    }

    public List<Attempt> findTop3ByAssignmentAndStudent(String tenantId, String assignmentId, String studentId) {
        return repository.findTop3ByTenantIdAndAssignmentIdAndStudentIdOrderByUpdatedAtDesc(tenantId, assignmentId, studentId);
    }

    public List<Attempt> findTop3ByAssignmentsAndStudent(
            String tenantId,
            Collection<String> assignmentIds,
            String studentId
    ) {
        if (assignmentIds == null || assignmentIds.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> retainedByAssignment = new HashMap<>();
        return repository.findByTenantIdAndAssignmentIdInAndStudentIdOrderByAssignmentIdAscUpdatedAtDesc(
                        tenantId, assignmentIds, studentId)
                .stream()
                .filter(attempt -> retainedByAssignment.merge(attempt.getAssignmentId(), 1, Integer::sum) <= 3)
                .toList();
    }

    public long countCompletedForLimit(String tenantId, String assignmentId, String quizId, String studentId) {
        return repository.countByStatusInForStudentAssignmentQuiz(
                tenantId,
                assignmentId,
                quizId,
                studentId,
                LmsStatePolicy.completedAttemptStates());
    }

    public List<Attempt> findByAssignment(String tenantId, String assignmentId) {
        return repository.findByTenantIdAndAssignmentIdOrderByUpdatedAtDesc(tenantId, assignmentId);
    }

    public com.erg.ergspringboot.lms.persistence.projection.AssignmentProgressProjection summarizeAssignmentProgress(
            String tenantId, String assignmentId) {
        return repository.summarizeAssignmentProgress(tenantId, assignmentId);
    }

    public List<Attempt> findByQuiz(String tenantId, String quizId) {
        return repository.findByTenantIdAndQuizIdOrderByUpdatedAtDesc(tenantId, quizId);
    }

    // Summary projection (no jsonb blobs) capped for the quiz-students list view; the full answers
    // payload belongs to the single-attempt detail endpoint, not this roster.
    public List<com.erg.ergspringboot.lms.persistence.projection.AttemptSummaryProjection> findQuizAttemptSummaries(
            String tenantId, String quizId) {
        return repository.findQuizAttemptSummaries(
                tenantId, quizId, org.springframework.data.domain.PageRequest.of(0, 500));
    }

    public org.springframework.data.domain.Page<com.erg.ergspringboot.lms.persistence.projection.AttemptSummaryProjection>
            findScopedQuizAttemptSummaries(String tenantId, String quizId, boolean allClasses,
                    java.util.Collection<String> classIds, boolean allUnits, java.util.Collection<String> unitIds,
                    org.springframework.data.domain.Pageable pageable) {
        return repository.findScopedQuizAttemptSummaries(
                tenantId, quizId, allClasses, classIds, allUnits, unitIds, pageable);
    }

    @Transactional("postgresTransactionManager")
    public Attempt save(Attempt attempt) {
        return repository.save(attempt);
    }

    /** Flushes immediately so unique-index races (single in-progress attempt) surface inside the caller. */
    @Transactional("postgresTransactionManager")
    public Attempt saveAndFlush(Attempt attempt) {
        return repository.saveAndFlush(attempt);
    }
}
