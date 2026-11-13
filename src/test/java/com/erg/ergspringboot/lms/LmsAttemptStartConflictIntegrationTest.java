package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StartAttemptRequest;
import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.lms.internal.attempt.LmsAttemptUseCase;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.AttemptRepository;
import com.erg.ergspringboot.lms.persistence.AssignmentResource;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
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
 * SRV-09: the 409s on POST /api/v1/lms/attempts. Starts run in their own committed transactions, two at a
 * time, the way a double click or two open tabs reach the server.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "erg.quiz.media.signing-secret=lms-attempt-race-test-signing-secret-0123456789")
@EnabledIf("postgresAvailable")
class LmsAttemptStartConflictIntegrationTest {
    private static final String TENANT = "tenant-attempt-race";
    private static final String QUIZ = "qz_math_g6_base";
    private static final int ROUNDS = 10;

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
    @Autowired private LmsAttemptUseCase attempts;
    @Autowired private AttemptRepository attemptRepository;
    private TransactionTemplate tx;

    @Autowired
    void transactions(@Qualifier("postgresTransactionManager") PlatformTransactionManager manager) {
        tx = new TransactionTemplate(manager);
    }

    @Test
    void doubleClickWithTheSameKeyReturnsOneAttemptToBoth() {
        for (int round = 0; round < ROUNDS; round++) {
            Learner learner = seed(null);
            String key = "start-" + UUID.randomUUID();
            List<Object> outcomes = race(() -> start(learner, key), () -> start(learner, key));
            assertThat(outcomes).as("round %d", round).allMatch(AttemptResponse.class::isInstance);
            assertThat(ids(outcomes)).as("round %d", round).hasSize(1);
        }
    }

    @Test
    void twoDevicesStartingTogetherShareTheOpenAttempt() {
        for (int round = 0; round < ROUNDS; round++) {
            Learner learner = seed(null);
            List<Object> outcomes = race(() -> start(learner, "start-" + UUID.randomUUID()),
                    () -> start(learner, "start-" + UUID.randomUUID()));
            assertThat(outcomes).as("round %d", round).allMatch(AttemptResponse.class::isInstance);
            assertThat(ids(outcomes)).as("round %d", round).hasSize(1);
        }
    }

    @Test
    void twoDevicesResumingTogetherBothGetTheOpenAttempt() {
        for (int round = 0; round < ROUNDS; round++) {
            Learner learner = seed(null);
            AttemptResponse open = start(learner, "start-" + UUID.randomUUID());
            List<Object> outcomes = race(() -> start(learner, "start-" + UUID.randomUUID()),
                    () -> start(learner, "start-" + UUID.randomUUID()));
            assertThat(outcomes).as("round %d", round).allMatch(AttemptResponse.class::isInstance);
            assertThat(ids(outcomes)).as("round %d", round).containsExactly(open.id());
        }
    }

    @Test
    void startAfterTheLastAllowedAttemptIsRejectedWithAttemptLimitReached() {
        Learner learner = seed(1);
        AttemptResponse first = start(learner, "start-" + UUID.randomUUID());
        try (var scope = TenantContext.scope(TenantContext.of(TENANT, learner.access().userId(), "student"))) {
            tx.executeWithoutResult(status -> attemptRepository.findByTenantIdAndId(TENANT, first.id()).orElseThrow()
                    .setStatus("submitted"));
        }

        assertThatThrownBy(() -> start(learner, "start-" + UUID.randomUUID()))
                .isInstanceOf(ConflictException.class).hasMessage("attempt limit reached");
    }

    private record Learner(String assignmentId, UserAccessContext access) {}

    private AttemptResponse start(Learner learner, String key) {
        try (var scope = TenantContext.scope(TenantContext.of(TENANT, learner.access().userId(), "student"))) {
            return tx.execute(status -> attempts.start(learner.access().userId(),
                    new StartAttemptRequest(learner.assignmentId(), QUIZ, "pkg-legacy", "hash-legacy"), key, learner.access()));
        }
    }

    private static List<Object> race(Supplier<Object> left, Supplier<Object> right) {
        CyclicBarrier gate = new CyclicBarrier(2);
        List<CompletableFuture<Object>> calls = new ArrayList<>();
        for (Supplier<Object> call : List.of(left, right)) {
            calls.add(CompletableFuture.supplyAsync(() -> {
                try {
                    gate.await();
                    return call.get();
                } catch (Exception failure) {
                    return failure;
                }
            }));
        }
        return calls.stream().map(CompletableFuture::join).toList();
    }

    private static List<String> ids(List<Object> outcomes) {
        return outcomes.stream().map(outcome -> ((AttemptResponse) outcome).id()).distinct().toList();
    }

    /** Commits one learner and one open assignment under fresh ids so rounds never collide. */
    private Learner seed(Integer attemptLimit) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String userId = "usr-race-" + suffix;
        try (var scope = TenantContext.scope(TenantContext.of(TENANT, userId, "student"))) {
            tx.executeWithoutResult(status -> {
                EducationUnit school = new EducationUnit();
                school.setId("school-" + suffix); school.setTenantId(TENANT); school.setName("Race " + suffix); school.setType("school");
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
                student.setAcademicClassId("class-" + suffix); student.setStudentCode("RACE-" + suffix); student.setFullName(suffix);
                student.setAuthUserId(userId); student.setStatus("active");
                entityManager.persist(student);
                Assignment assignment = new Assignment();
                assignment.setId("asg-" + suffix); assignment.setTenantId(TENANT); assignment.setAcademicClassId("class-" + suffix);
                assignment.setQuizId(QUIZ); assignment.setStudentIds(List.of("stu-" + suffix)); assignment.setStatus("active");
                assignment.setAssignedBy(userId); assignment.setRecipientMode("students");
                assignment.setDueAt(OffsetDateTime.now().plusDays(1)); assignment.setAttemptLimit(attemptLimit);
                entityManager.persist(assignment);
                AssignmentResource resource = new AssignmentResource();
                resource.setAssignmentId("asg-" + suffix); resource.setQuizId(QUIZ); resource.setTenantId(TENANT);
                resource.setOrderIndex(0); resource.setCreatedAt(OffsetDateTime.now());
                entityManager.persist(resource);
            });
        }
        return new Learner("asg-" + suffix, new UserAccessContext(userId, TENANT, List.of(), List.of(), null, null));
    }
}
