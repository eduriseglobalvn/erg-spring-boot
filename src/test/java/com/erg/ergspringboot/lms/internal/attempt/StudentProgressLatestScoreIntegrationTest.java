package com.erg.ergspringboot.lms.internal.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.lcms.persistence.quiz.runtime.StudentProgressRepository;
import com.erg.ergspringboot.lms.internal.core.LmsQuizService;
import com.erg.ergspringboot.lms.internal.core.LmsStudentService;
import com.erg.ergspringboot.lms.internal.quiz.runtime.QuizAttemptGradingService;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.lms.persistence.AttemptRepository;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.reporting.AssignmentProgressFilter;
import com.erg.ergspringboot.reporting.quiz.QuizReportService;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;

/**
 * DATA-01: a learner hands the same assignment in three times (40 → 60 → 90 %). The student_progress projection
 * must hold the latest hand-in as a percent, and the teacher's assignment progress report must show 90 and count
 * the graded hand-in as handed in. Not {@code @Transactional}: the projection commits in its own transaction.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "erg.quiz.media.signing-secret=student-progress-score-test-secret-0123456789")
@EnabledIf("postgresAvailable")
class StudentProgressLatestScoreIntegrationTest {
    private static final String TENANT = "tenant-progress-score";

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    static boolean postgresAvailable() {
        String configured = System.getProperty("erg.test.postgres.url");
        if (configured == null || configured.isBlank()) configured = System.getenv("ERG_TEST_POSTGRES_URL");
        if (configured != null && !configured.isBlank()) return true;
        try { return DockerClientFactory.instance().isDockerAvailable(); }
        catch (RuntimeException unavailable) { return false; }
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired @Qualifier("postgresTransactionManager") private PlatformTransactionManager transactions;
    @Autowired private LmsStudentService students;
    @Autowired private LmsQuizService quizzes;
    @Autowired private QuizAttemptGradingService grading;
    @Autowired private AttemptRepository attempts;
    @Autowired private StudentProgressRepository progress;
    @Autowired private QuizReportService reports;

    @BeforeEach
    void seed() {
        cleanup();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            entityManager.createNativeQuery("select set_config('app.tenant_id', :tenantId, true)")
                    .setParameter("tenantId", TENANT).getSingleResult();
            EducationUnit school = new EducationUnit();
            school.setId("sps-school"); school.setTenantId(TENANT); school.setName("SPS"); school.setType("school");
            entityManager.persist(school);
            User user = new User();
            user.setId("sps-user"); user.setTenantId(TENANT); user.setUsername("sps-user"); user.setEmail("sps-user@example.test");
            user.setFullName("SPS"); user.setStatus("ACTIVE"); user.setMustChangePassword(false);
            user.setIsProfileCompleted(true); user.setRecoveryEmailVerified(false);
            entityManager.persist(user);
            Classroom classroom = new Classroom();
            classroom.setId("sps-class"); classroom.setTenantId(TENANT); classroom.setSchoolId("sps-school");
            classroom.setName("SPS 6A1"); classroom.setGrade("6"); classroom.setAcademicYear("2026"); classroom.setStatus("active");
            entityManager.persist(classroom);
            Student student = new Student();
            student.setId("sps-student"); student.setTenantId(TENANT); student.setSchoolId("sps-school");
            student.setAcademicClassId("sps-class"); student.setStudentCode("SPS-1"); student.setFullName("SPS Student");
            student.setAuthUserId("sps-user"); student.setStatus("active");
            entityManager.persist(student);
            assignment = new Assignment();
            assignment.setId("sps-asg"); assignment.setTenantId(TENANT); assignment.setAcademicClassId("sps-class");
            assignment.setQuizId("qz_math_g6_base"); assignment.setStudentIds(List.of("sps-student"));
            assignment.setStatus("active"); assignment.setAssignedBy("sps-user"); assignment.setRecipientMode("students");
            assignment.setDueAt(OffsetDateTime.now().plusDays(1));
            entityManager.persist(assignment);
        });
    }

    private Assignment assignment;

    @AfterEach
    void cleanup() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (String table : List.of("student_progress", "assignment_progress_aggregates",
                    "report_reconciliation_queue", "lms_attempts", "assignment_resources", "lms_assignments",
                    "lms_students", "academic_classes", "users", "education_units")) {
                jdbc.update("delete from " + table + " where tenant_id = ?", TENANT);
            }
        });
    }

    @Test
    void progressScoreAndAssignmentReportFollowTheLatestHandIn() {
        OffsetDateTime base = OffsetDateTime.now().minusHours(1);
        AttemptProgressProjector projector = new AttemptProgressProjector(students, quizzes, grading);
        int minute = 0;
        // Ten one-point questions: the raw score (4, 6, 9) differs from the percent the reports show.
        for (double percent : new double[] {40, 60, 90}) {
            Attempt saved = attempts.saveAndFlush(graded("sps-att-" + (int) percent, percent, base.plusMinutes(minute += 10)));
            projector.projectAfterCommit(saved, assignment, TENANT);
        }

        var row = progress.findByTenantIdAndStudentUserIdAndQuizIdAndAssignmentId(TENANT, "sps-user", "qz_math_g6_base", "sps-asg")
                .orElseThrow();
        assertThat(row.getScore()).isEqualByComparingTo(BigDecimal.valueOf(90));

        var report = reports.assignmentReport(TENANT, "sps-asg", 0, 100);
        assertThat(report.averagePercent()).isEqualTo(90.0);
        assertThat(report.students()).singleElement().satisfies(student -> assertThat(student.score()).isEqualTo(90.0));
        // DATA-01 §2.3: a graded hand-in is handed in, never "not started".
        assertThat(report.submittedCount()).isEqualTo(1);
        assertThat(report.notStartedCount()).isZero();

        // fable/04 §2.2 step 4 reconciliation (student_progress has student_user_id, joined through lms_students).
        Long drift = jdbc.queryForObject("""
                select count(*) from student_progress p
                join lms_students s on s.tenant_id = p.tenant_id
                  and (s.auth_user_id = p.student_user_id or s.id = p.student_user_id)
                join lateral (
                  select a.percent from lms_attempts a
                  where a.tenant_id = p.tenant_id and a.assignment_id = p.assignment_id and a.student_id = s.id
                    and lower(a.status) in ('submitted','graded','completed','needs_review','review_required')
                  order by a.updated_at desc, a.id desc limit 1
                ) latest on true
                where p.tenant_id = ? and p.score is distinct from latest.percent::numeric(8,2)
                """, Long.class, TENANT);
        assertThat(drift).isZero();

        var filtered = reports.assignmentReport(TENANT, "sps-asg",
                new AssignmentProgressFilter(null, null, null, null, null, 0, 100));
        assertThat(filtered.averagePercent()).isEqualTo(90.0);
        assertThat(filtered.submittedCount()).isEqualTo(1);
        // pgjdbc sends timestamps untyped, so "? is null" needs the cast in the query to bind at all.
        filtered = reports.assignmentReport(TENANT, "sps-asg",
                new AssignmentProgressFilter(null, "graded", base.minusDays(1), base.plusDays(1), null, 0, 100));
        assertThat(filtered.averagePercent()).isEqualTo(90.0);
        assertThat(filtered.submittedCount()).isEqualTo(1);
        assertThat(filtered.notStartedCount()).isZero();
    }

    private Attempt graded(String id, double percent, OffsetDateTime at) {
        Attempt attempt = new Attempt();
        attempt.setId(id);
        attempt.setTenantId(TENANT);
        attempt.setAssignmentId("sps-asg");
        attempt.setQuizId("qz_math_g6_base");
        attempt.setStudentId("sps-student");
        attempt.setClassId("sps-class");
        attempt.setStatus("graded");
        attempt.setScore(percent / 10);
        attempt.setMaxScore(10.0);
        attempt.setPercent(percent);
        attempt.setPassed(percent >= 50);
        attempt.setTotalQuestions(10);
        attempt.setSubmittedCount(10);
        attempt.setStartedAt(at.minusMinutes(5));
        attempt.setSubmittedAt(at);
        attempt.setUpdatedAt(at);
        return attempt;
    }
}
