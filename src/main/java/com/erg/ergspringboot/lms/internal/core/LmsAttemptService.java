package com.erg.ergspringboot.lms.internal.core;

import com.erg.ergspringboot.lms.LmsStatePolicy;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.lms.persistence.AttemptRepository;
import com.erg.ergspringboot.lms.persistence.projection.AttemptSummaryProjection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class LmsAttemptService {

    private final AttemptRepository repository;
    @PersistenceContext
    private EntityManager entityManager;

    public LmsAttemptService(AttemptRepository repository) {
        this.repository = repository;
    }

    public Optional<Attempt> findByTenantIdAndId(String tenantId, String id) {
        return repository.findByTenantIdAndId(tenantId, id);
    }

    public Optional<Attempt> findInProgress(String tenantId, String assignmentId, String quizId, String studentId) {
        return repository.findInProgress(tenantId, assignmentId, quizId, studentId);
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

    /** Latest handed-in attempt (submitted/graded/needs_review); never an in-progress one. */
    public Optional<Attempt> findLatestSubmitted(String tenantId, String assignmentId, String studentId) {
        return repository.findLatestByStatuses(tenantId, assignmentId, studentId,
                List.of("submitted", "graded", "needs_review"), org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().findFirst();
    }

    /** Newest three attempts per assignment, answers not loaded (score list view). */
    public List<AttemptSummaryProjection> findTop3ByAssignmentsAndStudent(
            String tenantId,
            Collection<String> assignmentIds,
            String studentId
    ) {
        if (assignmentIds == null || assignmentIds.isEmpty()) {
            return List.of();
        }
        return repository.findTop3SummariesByAssignmentsAndStudent(tenantId, assignmentIds, studentId);
    }

    public long countCompletedForLimit(String tenantId, String assignmentId, String quizId, String studentId) {
        return repository.countByStatusInForStudentAssignmentQuiz(
                tenantId,
                assignmentId,
                quizId,
                studentId,
                LmsStatePolicy.completedAttemptStates());
    }

    public com.erg.ergspringboot.lms.persistence.projection.AssignmentProgressProjection summarizeAssignmentProgress(
            String tenantId, String assignmentId) {
        return repository.summarizeAssignmentProgress(tenantId, assignmentId);
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

    /**
     * Holds starts of one learner on one assignment quiz until the transaction ends, so a double click or a second
     * tab waits and then resumes the attempt the first one opened instead of failing with 409 or 500.
     */
    @Transactional("postgresTransactionManager")
    public void lockStart(String tenantId, String assignmentId, String quizId, String studentId) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .setParameter("key", "lms-attempt-start:" + tenantId + ":" + assignmentId + ":" + quizId + ":" + studentId)
                .getSingleResult();
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
