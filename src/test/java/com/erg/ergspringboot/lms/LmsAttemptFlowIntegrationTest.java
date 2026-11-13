package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StartAttemptRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.SubmitAttemptRequest;
import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.lms.internal.attempt.LmsAttemptUseCase;
import com.erg.ergspringboot.lms.internal.readmodel.LmsClassWorkspaceReadUseCase;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.AssignmentResource;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.lms.persistence.AttemptRepository;
import com.erg.ergspringboot.lms.persistence.AttemptExpiryRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizPackageRepository;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/**
 * P0-5 end-to-end proof against PostgreSQL + Flyway: an assignment created for a Student whose login id
 * differs from Student.id can be started and submitted by that learner, another learner gets 403, and
 * V115's partial unique index rejects a second in-progress attempt.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "erg.quiz.media.signing-secret=lms-attempt-flow-test-signing-secret-0123456789")
@Transactional
@EnabledIf("postgresAvailable")
class LmsAttemptFlowIntegrationTest {
    private static final String TENANT = "tenant-attempt-flow";

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
    @Autowired private LmsClassWorkspaceReadUseCase classWorkspace;
    @Autowired private AttemptExpiryRepository expiryRepository;
    @Autowired private QuizPackageRepository packageRepository;

    @Test
    void learnerLinkedByAuthUserIdStartsAndSubmitsWhileOtherLearnerIsForbidden() {
        seed();
        var pkg = packageRepository.findByQuizVersionIdAndTenantId("qv_math_g6_v1", "erg").orElse(null);
        String packageId = pkg == null ? "pkg-legacy" : pkg.getId();
        String packageHash = pkg == null ? "hash-legacy" : pkg.getContentHash();
        UserAccessContext learnerA = new UserAccessContext("usr-flow-a", TENANT, List.of(), List.of(), null, null);
        UserAccessContext learnerB = new UserAccessContext("usr-flow-b", TENANT, List.of(), List.of(), null, null);

        var started = attempts.start("usr-flow-a", new StartAttemptRequest("asg-flow", "qz_math_g6_base", packageId, packageHash),
                null, learnerA);
        assertThat(started.studentId()).isEqualTo("stu-flow-a");

        assertThatThrownBy(() -> attempts.submit(started.id(),
                new SubmitAttemptRequest(Map.of(), null, null, null, Map.of(), List.of()), learnerB))
                .isInstanceOf(ForbiddenException.class);

        var submitted = attempts.submit(started.id(),
                new SubmitAttemptRequest(Map.of(), null, null, null, Map.of(), List.of()), learnerA);
        assertThat(submitted).isNotNull();
        entityManager.flush();
        assertThat(attemptRepository.findByTenantIdAndId(TENANT, started.id()).orElseThrow().getStatus())
                .isIn("submitted", "needs_review");
    }

    @Test
    void singleInProgressIndexRejectsASecondConcurrentAttempt() {
        seed();
        attemptRepository.saveAndFlush(inProgress("att-flow-1"));

        assertThatThrownBy(() -> attemptRepository.saveAndFlush(inProgress("att-flow-2")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void classRosterShowsEachStudentsOwnLatestHandedInScore() {
        seed();
        OffsetDateTime base = OffsetDateTime.now().minusHours(1);
        attemptRepository.saveAndFlush(handedIn("att-roster-a1", "stu-flow-a", 40.0, base));
        attemptRepository.saveAndFlush(handedIn("att-roster-a2", "stu-flow-a", 90.0, base.plusMinutes(10)));
        attemptRepository.saveAndFlush(handedIn("att-roster-b1", "stu-flow-b", 50.0, base));

        var roster = classWorkspace.query(TENANT, "class-flow", null, null, null, null, null, null, null, true, false)
                .students().items();
        assertThat(roster).extracting("id", "averagePercent", "completedAssignmentCount")
                .containsExactlyInAnyOrder(tuple("stu-flow-a", 90, 1), tuple("stu-flow-b", 50, 1));

        var withoutGradeRead = classWorkspace.query(TENANT, "class-flow", null, null, null, null, null, null, null, false, false)
                .students().items();
        assertThat(withoutGradeRead).extracting("averagePercent").containsOnlyNulls();
    }

    @Test
    void assignmentProgressCountsStudentsByTheirLatestHandIn() {
        seed();
        entityManager.persist(student("stu-flow-c", null, "FLOW-C"));
        OffsetDateTime base = OffsetDateTime.now().minusHours(1);
        // A: submitted, then a graded retake -> one handed-in student, not awaiting review.
        attemptRepository.saveAndFlush(handedIn("att-prog-a1", "stu-flow-a", "submitted", base));
        attemptRepository.saveAndFlush(handedIn("att-prog-a2", "stu-flow-a", "graded", base.plusMinutes(10)));
        // B: an old needs_review hand-in, then a completed one -> handed in, no longer awaiting review.
        attemptRepository.saveAndFlush(handedIn("att-prog-b1", "stu-flow-b", "needs_review", base));
        attemptRepository.saveAndFlush(handedIn("att-prog-b2", "stu-flow-b", "completed", base.plusMinutes(5)));
        // C: the only hand-in awaits review.
        attemptRepository.saveAndFlush(handedIn("att-prog-c1", "stu-flow-c", "review_required", base));

        var summary = attemptRepository.summarizeAssignmentProgress(TENANT, "asg-flow");
        assertThat(summary.submittedCount()).isEqualTo(3);
        assertThat(summary.needsReviewCount()).isEqualTo(1);
        assertThat(summary.inProgressCount()).isZero();

        // A retake in progress does not move A out of "handed in" nor count A twice.
        attemptRepository.saveAndFlush(handedIn("att-prog-a3", "stu-flow-a", "in_progress", base.plusMinutes(30)));
        summary = attemptRepository.summarizeAssignmentProgress(TENANT, "asg-flow");
        assertThat(summary.submittedCount()).isEqualTo(3);
        assertThat(summary.inProgressCount()).isZero();
    }

    @Test
    void assignmentProgressCountsAStudentWhoHasNotHandedInAsInProgress() {
        seed();
        attemptRepository.saveAndFlush(inProgress("att-prog-only"));

        var summary = attemptRepository.summarizeAssignmentProgress(TENANT, "asg-flow");
        assertThat(summary.submittedCount()).isZero();
        assertThat(summary.inProgressCount()).isEqualTo(1);
        assertThat(summary.needsReviewCount()).isZero();
    }

    private Attempt handedIn(String id, String studentId, String status, OffsetDateTime at) {
        Attempt attempt = inProgress(id);
        attempt.setStudentId(studentId);
        attempt.setStatus(status);
        attempt.setStartedAt(at);
        if (!"in_progress".equals(status)) attempt.setSubmittedAt(at);
        attempt.setUpdatedAt(at);
        return attempt;
    }

    @Test
    void expirySweepFindsAndClosesAnAttemptPastItsDuration() {
        seed();
        Assignment assignment = entityManager.find(Assignment.class, "asg-flow");
        assignment.setMaxDurationMinutes(30);
        Attempt stale = inProgress("att-flow-stale");
        stale.setStartedAt(OffsetDateTime.now().minusHours(2));
        attemptRepository.saveAndFlush(stale);
        OffsetDateTime now = OffsetDateTime.now();

        // The sweep query spans every tenant; CI shares one database with other suites, so look at this tenant only.
        assertThat(staleIdsForThisTenant(now)).containsExactly("att-flow-stale");
        assertThat(attempts.closeIfExpired(TENANT, "att-flow-stale", now)).isTrue();
        entityManager.flush();
        assertThat(attemptRepository.findByTenantIdAndId(TENANT, "att-flow-stale").orElseThrow().getStatus())
                .isNotEqualTo("in_progress");
        assertThat(staleIdsForThisTenant(now)).isEmpty();
    }

    @Test
    void pagedRosterAppliesClassAndUnitScopeInSql() {
        seed();
        attemptRepository.saveAndFlush(inProgress("att-flow-1"));
        var page = org.springframework.data.domain.PageRequest.of(0, 10);

        assertThat(attemptRepository.findScopedQuizAttemptSummaries(TENANT, "qz_math_g6_base",
                false, List.of("class-flow"), false, List.of("school-flow"), page).getTotalElements()).isEqualTo(1);
        assertThat(attemptRepository.findScopedQuizAttemptSummaries(TENANT, "qz_math_g6_base",
                true, List.of("x"), true, List.of("x"), page).getContent()).extracting("id").containsExactly("att-flow-1");
        assertThat(attemptRepository.findScopedQuizAttemptSummaries(TENANT, "qz_math_g6_base",
                false, List.of("class-other"), true, List.of("x"), page).getTotalElements()).isZero();
        assertThat(attemptRepository.findScopedQuizAttemptSummaries(TENANT, "qz_math_g6_base",
                true, List.of("x"), false, List.of("school-other"), page).getTotalElements()).isZero();
    }

    @Test
    void classAverageCountsOnlyEachStudentsLatestHandedInAttempt() {
        seed();
        OffsetDateTime base = OffsetDateTime.now().minusHours(1);
        attemptRepository.saveAndFlush(graded("att-avg-a1", "stu-flow-a", 40.0, base));
        attemptRepository.saveAndFlush(graded("att-avg-a2", "stu-flow-a", 60.0, base.plusMinutes(10)));
        attemptRepository.saveAndFlush(graded("att-avg-a3", "stu-flow-a", 90.0, base.plusMinutes(20)));

        var single = attemptRepository.summarizeClassWorkspaceScores(TENANT, "class-flow");
        assertThat(single.averagePercent()).isEqualTo(90.0);
        assertThat(single.completedAttemptCount()).isEqualTo(1L);

        attemptRepository.saveAndFlush(graded("att-avg-b1", "stu-flow-b", 50.0, base));
        var both = attemptRepository.summarizeClassWorkspaceScores(TENANT, "class-flow");
        assertThat(both.averagePercent()).isEqualTo(70.0);
        assertThat(both.completedAttemptCount()).isEqualTo(2L);
        assertThat(both.attemptedStudentCount()).isEqualTo(2L);
        assertThat(both.bestPercent()).isEqualTo(90.0);
    }

    private Attempt graded(String id, String studentId, double percent, OffsetDateTime at) {
        Attempt attempt = inProgress(id);
        attempt.setStudentId(studentId);
        attempt.setStatus("graded");
        attempt.setPercent(percent);
        attempt.setStartedAt(at);
        attempt.setSubmittedAt(at);
        attempt.setUpdatedAt(at);
        return attempt;
    }

    private List<String> staleIdsForThisTenant(OffsetDateTime now) {
        return expiryRepository.findExpiredCandidates(now, 10_000).stream()
                .filter(ref -> TENANT.equals(ref.getTenantId())).map(ref -> ref.getId()).toList();
    }

    private Attempt handedIn(String id, String studentId, double percent, OffsetDateTime at) {
        Attempt attempt = inProgress(id);
        attempt.setStudentId(studentId);
        attempt.setStatus("graded");
        attempt.setPercent(percent);
        attempt.setStartedAt(at);
        attempt.setSubmittedAt(at);
        attempt.setUpdatedAt(at);
        return attempt;
    }

    private Attempt inProgress(String id) {
        Attempt attempt = new Attempt();
        attempt.setId(id);
        attempt.setTenantId(TENANT);
        attempt.setAssignmentId("asg-flow");
        attempt.setQuizId("qz_math_g6_base");
        attempt.setStudentId("stu-flow-a");
        attempt.setStatus("in_progress");
        attempt.setStartedAt(OffsetDateTime.now());
        attempt.setUpdatedAt(OffsetDateTime.now());
        return attempt;
    }

    private void seed() {
        entityManager.createNativeQuery("select set_config('app.tenant_id', :tenantId, true)")
                .setParameter("tenantId", TENANT).getSingleResult();
        EducationUnit school = new EducationUnit();
        school.setId("school-flow"); school.setTenantId(TENANT); school.setName("Flow School"); school.setType("school");
        entityManager.persist(school);
        for (String userId : List.of("usr-flow-a", "usr-flow-b", "usr-flow-teacher")) {
            User user = new User();
            user.setId(userId); user.setTenantId(TENANT); user.setUsername(userId); user.setEmail(userId + "@example.test");
            user.setFullName(userId); user.setStatus("ACTIVE"); user.setMustChangePassword(false);
            user.setIsProfileCompleted(true); user.setRecoveryEmailVerified(false);
            entityManager.persist(user);
        }
        Classroom classroom = new Classroom();
        classroom.setId("class-flow"); classroom.setTenantId(TENANT); classroom.setSchoolId("school-flow");
        classroom.setName("Flow 6A1"); classroom.setGrade("6"); classroom.setAcademicYear("2026"); classroom.setStatus("active");
        entityManager.persist(classroom);
        entityManager.persist(student("stu-flow-a", "usr-flow-a", "FLOW-A"));
        entityManager.persist(student("stu-flow-b", "usr-flow-b", "FLOW-B"));

        Assignment assignment = new Assignment();
        assignment.setId("asg-flow");
        assignment.setTenantId(TENANT);
        assignment.setAcademicClassId("class-flow");
        assignment.setQuizId("qz_math_g6_base");
        assignment.setStudentIds(List.of("stu-flow-a"));
        assignment.setStatus("active");
        assignment.setAssignedBy("usr-flow-teacher");
        assignment.setRecipientMode("students");
        assignment.setDueAt(OffsetDateTime.now().plusDays(1));
        entityManager.persist(assignment);
        AssignmentResource resource = new AssignmentResource();
        resource.setAssignmentId("asg-flow");
        resource.setQuizId("qz_math_g6_base");
        resource.setTenantId(TENANT);
        resource.setOrderIndex(0);
        resource.setCreatedAt(OffsetDateTime.now());
        entityManager.persist(resource);
        entityManager.flush();
    }

    private Student student(String id, String authUserId, String code) {
        Student student = new Student();
        student.setId(id); student.setTenantId(TENANT); student.setSchoolId("school-flow");
        student.setAcademicClassId("class-flow"); student.setStudentCode(code); student.setFullName(code);
        student.setAuthUserId(authUserId); student.setStatus("active");
        return student;
    }
}
