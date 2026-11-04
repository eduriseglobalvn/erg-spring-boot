package com.erg.ergspringboot.lms.internal.attempt;

import com.erg.ergspringboot.lms.internal.core.LmsQuizService;
import com.erg.ergspringboot.lms.internal.core.LmsStudentService;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.Quiz;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.internal.quiz.runtime.QuizAttemptGradingService;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The student_progress read projection is derived data: it is written after the attempt transaction
 * committed (in its own transaction), so a projection failure can never roll back a graded submission.
 */
final class AttemptProgressProjector {
    // Logged under the use-case category so existing log routing/alerts keep matching.
    private static final Logger log = LoggerFactory.getLogger(LmsAttemptUseCase.class);
    private final LmsStudentService students;
    private final LmsQuizService quizzes;
    private final QuizAttemptGradingService grading;

    AttemptProgressProjector(LmsStudentService students, LmsQuizService quizzes, QuizAttemptGradingService grading) {
        this.students = students;
        this.quizzes = quizzes;
        this.grading = grading;
    }

    /**
     * Resolves the projection inputs inside the caller's transaction and registers the write to run after commit
     * (or runs it immediately when no transaction synchronization is active).
     */
    void projectAfterCommit(Attempt a, Assignment assignment, String tenant) {
        String classId = assignment == null || blank(assignment.getAcademicClassId()) == null ? a.getClassId() : assignment.getAcademicClassId();
        Student student = students.findByTenantIdAndId(tenant, a.getStudentId())
                .or(() -> students.findFirstByTenantIdAndAuthUserId(tenant, a.getStudentId())).orElse(null);
        String studentName = student == null ? null : student.getFullName();
        String progressId = student == null || student.getAuthUserId() == null ? a.getStudentId() : student.getAuthUserId();
        String title = quizzes.findByTenantIdAndId(tenant, a.getQuizId()).map(Quiz::getTitle).orElse(null);
        int answered = a.getSubmittedCount() == null ? 0 : a.getSubmittedCount();
        int total = a.getTotalQuestions() == null ? 0 : a.getTotalQuestions();
        BigDecimal score = BigDecimal.valueOf(a.getScore() == null ? 0 : a.getScore());
        boolean passed = Boolean.TRUE.equals(a.getPassed());
        String status = a.getStatus();
        Runnable projection = () -> {
            try {
                grading.projectProgress(tenant, progressId, studentName, a.getQuizId(), title, a.getAssignmentId(),
                        classId, a.getMode(), status, answered, total, score, passed, a.getStartedAt(), a.getSubmittedAt());
            } catch (RuntimeException ex) {
                // The attempt is already committed; the projection is rebuilt on the next attempt write.
                log.warn("Failed to project student_progress for attempt {}: {}", a.getId(), ex.getMessage());
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            projection.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { projection.run(); }
        });
    }

    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
