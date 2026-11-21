package com.erg.ergspringboot.lms.internal.attempt;

import com.erg.ergspringboot.lms.LmsStudentIdentityLookup;
import com.erg.ergspringboot.lms.QuizStartInfo;
import com.erg.ergspringboot.lms.internal.core.LmsStudentService;
import com.erg.ergspringboot.lms.internal.quiz.runtime.QuizAttemptGradingService;
import com.erg.ergspringboot.lms.internal.quiz.runtime.QuizRuntimePackageService;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.lms.persistence.Student;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The student_progress projection is written inside the attempt's own transaction (one connection, one commit).
 * QuizAttemptGradingService.projectProgress contains its own database failures on a JDBC savepoint; the catch here
 * covers everything before that write, so a projection failure never rolls back a graded submission.
 */
final class AttemptProgressProjector {
    // Logged under the use-case category so existing log routing/alerts keep matching.
    private static final Logger log = LoggerFactory.getLogger(LmsAttemptUseCase.class);
    private final LmsStudentIdentityLookup identities;
    private final LmsStudentService students;
    private final QuizRuntimePackageService packages;
    private final QuizAttemptGradingService grading;

    AttemptProgressProjector(LmsStudentIdentityLookup identities, LmsStudentService students,
            QuizRuntimePackageService packages, QuizAttemptGradingService grading) {
        this.identities = identities;
        this.students = students;
        this.packages = packages;
        this.grading = grading;
    }

    /** Writes the projection in the caller's transaction and returns the learner's login user id. */
    String project(Attempt a, Assignment assignment, String tenant) {
        return project(a, assignment, tenant, null);
    }

    /** {@code callerUserId}: the caller's login, only used to hit the cached lookup when the attempt is the caller's own. */
    String project(Attempt a, Assignment assignment, String tenant, String callerUserId) {
        String classId = assignment == null || blank(assignment.getAcademicClassId()) == null ? a.getClassId() : assignment.getAcademicClassId();
        Student student = student(a, tenant, callerUserId);
        String studentName = student == null ? null : student.getFullName();
        String progressId = userId(a, student);
        guarded(a, () -> write(a, tenant, classId, studentName, progressId));
        return progressId;
    }

    private void write(Attempt a, String tenant, String classId, String studentName, String progressId) {
        QuizStartInfo info = packages.startInfo(tenant, a.getQuizId(), blank(a.getQuizVersionId()));
        String title = info == null ? null : info.quizTitle();
        int answered = a.getSubmittedCount() == null ? 0 : a.getSubmittedCount();
        int total = a.getTotalQuestions() == null ? 0 : a.getTotalQuestions();
        // Reports read student_progress.score as 0-100 (DATA-01), so project the percent, not raw points.
        BigDecimal score = BigDecimal.valueOf(a.getPercent() == null ? 0 : a.getPercent());
        boolean passed = Boolean.TRUE.equals(a.getPassed());
        grading.projectProgress(tenant, progressId, studentName, a.getQuizId(), title, a.getAssignmentId(),
                classId, a.getMode(), a.getStatus(), answered, total, score, passed, a.getStartedAt(), a.getSubmittedAt());
    }

    /** Any failure is logged (attempt id only) and swallowed. */
    private void guarded(Attempt a, Runnable write) {
        try {
            write.run();
        } catch (RuntimeException ex) {
            log.warn("EXAM_PROGRESS_PROJECTION_FAILED attemptId={} cause={}", a.getId(), ex.getClass().getSimpleName());
        }
    }

    /** Login user id of the attempt's learner (attempts store Student.id; legacy rows store the user id itself). */
    String studentUserId(Attempt a, String tenant) {
        return userId(a, student(a, tenant, null));
    }

    /** Attempts store Student.id: the caller's cached identity answers when it is that student, else one id lookup. */
    private Student student(Attempt a, String tenant, String callerUserId) {
        if (callerUserId != null && !callerUserId.isBlank()) {
            var own = identities.byAuthUser(tenant, callerUserId);
            if (own.exists() && own.studentId().equals(a.getStudentId())) return own.toStudent(tenant);
        }
        return students.findByTenantIdAndId(tenant, a.getStudentId()).orElseGet(() -> {
            var legacy = identities.byAuthUser(tenant, a.getStudentId()); // legacy rows store the login id itself
            return legacy.exists() ? legacy.toStudent(tenant) : null;
        });
    }

    private String userId(Attempt a, Student student) {
        return student == null || student.getAuthUserId() == null ? a.getStudentId() : student.getAuthUserId();
    }

    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
