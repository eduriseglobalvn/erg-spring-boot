package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptDraftRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptSubmitResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizPackageResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StartAttemptRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StudentAssignmentListResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.SubmitAttemptRequest;
import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.Quiz;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizPackage;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersion;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionQuestion;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.AssignmentResource;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.platform.performance.RequestExecutionContext;
import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;

/**
 * Statement budget of one exam session (the k6 erg-exam-session.js flow): list assignments, load the package,
 * start, save a draft, submit. Each call runs like a request: no outer transaction, counted by the same
 * Hibernate statement inspector that feeds {@code HibernateQueryCountFilter}. Plain JDBC (sync receipts,
 * tenant GUCs, modulith event publication) is not counted there.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "erg.quiz.media.signing-secret=lms-exam-hot-path-test-signing-secret-0123456789")
@EnabledIf("postgresAvailable")
class LmsExamHotPathQueryBudgetIntegrationTest {
    private static final String TENANT = "tenant-exam-hot-path";
    private static final int QUESTIONS = 20;

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

    @Autowired private EntityManager entityManager;
    @Autowired private LmsOperations lms;
    private TransactionTemplate tx;

    @Autowired
    void transactions(@Qualifier("postgresTransactionManager") PlatformTransactionManager manager) {
        tx = new TransactionTemplate(manager);
    }

    @Test
    void examSessionStaysWithinItsStatementBudget() {
        Seed seed = seed();
        UserAccessContext access = new UserAccessContext(seed.userId(), TENANT,
                List.of("school-" + seed.suffix()), List.of("class-" + seed.suffix()), null, null);

        Counted<StudentAssignmentListResponse> home = count(seed, () -> lms.listStudentAssignments(seed.userId(), access, 0, 20));
        Counted<QuizPackageResponse> pkg = count(seed, () -> lms.getQuizPackage(seed.quizId(), seed.assignmentId(), access));
        Counted<AttemptResponse> start = count(seed, () -> lms.startAttempt(seed.userId(),
                new StartAttemptRequest(seed.assignmentId(), seed.quizId(), pkg.value().id(), pkg.value().packageHash()),
                "start-" + seed.suffix(), access));
        Map<String, Object> answers = new LinkedHashMap<>();
        for (int i = 0; i < QUESTIONS; i++) answers.put("q" + i, Map.of("choiceId", i % 4 == 0 ? "b" : "a"));
        Counted<AttemptResponse> draft = count(seed, () -> lms.saveDraft(start.value().id(),
                new AttemptDraftRequest(Map.of("q0", Map.of("choiceId", "a"), "q1", Map.of("choiceId", "a")), pkg.value().packageHash(), null, null, null)
                        .withIdempotencyKey("draft-" + seed.suffix()), access));
        Counted<AttemptSubmitResponse> submit = count(seed, () -> lms.submitAttempt(start.value().id(),
                new SubmitAttemptRequest(answers, null, pkg.value().packageHash(), null, null, null, "submit-" + seed.suffix()),
                access));

        System.out.printf("EXAM_HOT_PATH_STATEMENTS home=%d package=%d start=%d draft=%d submit=%d%n",
                home.statements(), pkg.statements(), start.statements(), draft.statements(), submit.statements());

        // Behaviour the budget must not change.
        assertThat(home.value().items()).hasSize(1);
        assertThat(start.value().status()).isEqualTo("in_progress");
        assertThat(draft.value().answers()).containsKeys("q0", "q1");
        assertThat(submit.value().maxScore()).isEqualTo(QUESTIONS);
        assertThat(submit.value().score()).isEqualTo(QUESTIONS - QUESTIONS / 4);
        assertThat(count("select count(*) from attempt_answers where attempt_id = ?1", start.value().id())).isEqualTo(QUESTIONS);
        assertThat(count("select count(*) from attempt_events where attempt_id = ?1 and type = 'attempt_submitted'", start.value().id())).isEqualTo(1);
        assertThat(count("select count(*) from lms_attempts where id = ?1 and status = 'submitted'", start.value().id())).isEqualTo(1);
        assertThat(count("select count(*) from student_progress where tenant_id = ?1 and student_user_id = ?2 and status = 'submitted'",
                TENANT, seed.userId())).isEqualTo(1);
        // A replayed submit returns the stored result.
        AttemptSubmitResponse replay = asLearner(seed, () -> lms.submitAttempt(start.value().id(),
                new SubmitAttemptRequest(answers, null, pkg.value().packageHash(), null, null, null, "submit-" + seed.suffix()), access));
        assertThat(replay.score()).isEqualTo(submit.value().score());

        assertThat(home.statements()).as("GET students/me/assignments").isLessThanOrEqualTo(BUDGET_HOME);
        assertThat(pkg.statements()).as("GET quizzes/{id}/package").isLessThanOrEqualTo(BUDGET_PACKAGE);
        assertThat(start.statements()).as("POST attempts").isLessThanOrEqualTo(BUDGET_START);
        assertThat(draft.statements()).as("PATCH attempts/{id}/draft").isLessThanOrEqualTo(BUDGET_DRAFT);
        assertThat(submit.statements()).as("POST attempts/{id}/submit").isLessThanOrEqualTo(BUDGET_SUBMIT);
    }

    // Measured before the fix: home 3, package 7, start 13, draft 4, submit 37 (20 questions).
    private static final int BUDGET_HOME = 3;
    private static final int BUDGET_PACKAGE = 7;
    private static final int BUDGET_START = 9;
    private static final int BUDGET_DRAFT = 4;
    // First submit of a version in this JVM; later ones also skip the question and version reads (11).
    private static final int BUDGET_SUBMIT = 13;

    private record Seed(String suffix, String userId, String assignmentId, String quizId) {}
    private record Counted<T>(T value, long statements) {}

    private <T> Counted<T> count(Seed seed, Supplier<T> call) {
        try (var request = RequestExecutionContext.open("exam-hot-path")) {
            T value = asLearner(seed, call);
            return new Counted<>(value, RequestExecutionContext.currentSnapshot().statementCount());
        }
    }

    private <T> T asLearner(Seed seed, Supplier<T> call) {
        try (var scope = TenantContext.scope(TenantContext.of(TENANT, seed.userId(), "student"))) {
            return call.get();
        }
    }

    private long count(String sql, Object... params) {
        try (var scope = TenantContext.scope(TenantContext.of(TENANT, "probe", "student"))) {
            return tx.execute(status -> {
                var query = entityManager.createNativeQuery(sql);
                for (int i = 0; i < params.length; i++) query.setParameter(i + 1, params[i]);
                return ((Number) query.getSingleResult()).longValue();
            });
        }
    }

    /** One learner, one open assignment pinning a published 20-question version with its package. */
    private Seed seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String userId = "usr-hot-" + suffix;
        String quizId = "qz-hot-" + suffix;
        String versionId = "qv-hot-" + suffix;
        try (var scope = TenantContext.scope(TenantContext.of(TENANT, userId, "student"))) {
            tx.executeWithoutResult(status -> {
                EducationUnit school = new EducationUnit();
                school.setId("school-" + suffix); school.setTenantId(TENANT); school.setName("Hot " + suffix); school.setType("school");
                entityManager.persist(school);
                User user = new User();
                user.setId(userId); user.setTenantId(TENANT); user.setUsername(userId); user.setEmail(userId + "@example.test");
                user.setFullName(userId); user.setStatus("ACTIVE"); user.setMustChangePassword(false);
                user.setIsProfileCompleted(true); user.setRecoveryEmailVerified(false);
                entityManager.persist(user);
                Classroom classroom = new Classroom();
                classroom.setId("class-" + suffix); classroom.setTenantId(TENANT); classroom.setSchoolId("school-" + suffix);
                classroom.setName("6A " + suffix); classroom.setGrade("6"); classroom.setAcademicYear("2026"); classroom.setStatus("active");
                entityManager.persist(classroom);
                Student student = new Student();
                student.setId("stu-" + suffix); student.setTenantId(TENANT); student.setSchoolId("school-" + suffix);
                student.setAcademicClassId("class-" + suffix); student.setStudentCode("HOT-" + suffix); student.setFullName("Hoc sinh " + suffix);
                student.setAuthUserId(userId); student.setStatus("active");
                entityManager.persist(student);

                Quiz quiz = new Quiz();
                quiz.setId(quizId); quiz.setTenantId(TENANT); quiz.setTitle("Kiem tra " + suffix); quiz.setKind("test");
                quiz.setStatus("published"); quiz.setVersion(1);
                entityManager.persist(quiz);
                QuizVersion version = new QuizVersion();
                version.setId(versionId); version.setQuizId(quizId); version.setTenantId(TENANT); version.setVersionLabel("v1");
                version.setVersionNumber(1); version.setRuntimeQuiz(Map.of("settings", Map.of("mode", "test", "passPercent", 50)));
                version.setAnswerKey(Map.of()); version.setGradingMode("server-authoritative"); version.setSchemaVersion(1);
                version.setContentHash("hash-" + suffix); version.setPublishedAt(OffsetDateTime.now()); version.setIsActive(true);
                entityManager.persist(version);
                entityManager.flush();
                quiz.setCurrentVersionId(versionId);
                for (int i = 0; i < QUESTIONS; i++) {
                    QuizVersionQuestion question = new QuizVersionQuestion();
                    question.setQuizVersionId(versionId); question.setTenantId(TENANT); question.setRuntimeQuestionId("q" + i);
                    question.setKind("single_choice"); question.setPoints(BigDecimal.ONE); question.setAutoGradable(true);
                    question.setGradingPolicy("auto"); question.setOrderIndex(i);
                    question.setBody(Map.of("id", "q" + i, "choices", List.of(Map.of("id", "a"), Map.of("id", "b"))));
                    question.setAnswerKey(Map.of("correctChoiceId", "a"));
                    entityManager.persist(question);
                }
                QuizPackage pkg = new QuizPackage();
                pkg.setId("pkg-hot-" + suffix); pkg.setQuizId(quizId); pkg.setQuizVersionId(versionId); pkg.setQuizVersion("v1");
                pkg.setTenantId(TENANT); pkg.setContentHash("hash-" + suffix); pkg.setGradingMode("server-authoritative");
                pkg.setSchemaVersion(1); pkg.setPackageFormat("single"); pkg.setGeneratedAt(OffsetDateTime.now());
                pkg.setPackageJson(Map.of("quiz", Map.of("id", quizId, "settings", Map.of("mode", "test"))));
                entityManager.persist(pkg);

                Assignment assignment = new Assignment();
                assignment.setId("asg-" + suffix); assignment.setTenantId(TENANT); assignment.setAcademicClassId("class-" + suffix);
                assignment.setQuizId(quizId); assignment.setStudentIds(List.of("stu-" + suffix)); assignment.setStatus("active");
                assignment.setAssignedBy(userId); assignment.setRecipientMode("students");
                assignment.setDueAt(OffsetDateTime.now().plusDays(1));
                entityManager.persist(assignment);
                AssignmentResource resource = new AssignmentResource();
                resource.setAssignmentId("asg-" + suffix); resource.setQuizId(quizId); resource.setTenantId(TENANT);
                resource.setQuizVersionId(versionId); resource.setQuizVersionLabel("v1");
                resource.setOrderIndex(0); resource.setCreatedAt(OffsetDateTime.now());
                entityManager.persist(resource);
            });
        }
        return new Seed(suffix, userId, "asg-" + suffix, quizId);
    }
}
