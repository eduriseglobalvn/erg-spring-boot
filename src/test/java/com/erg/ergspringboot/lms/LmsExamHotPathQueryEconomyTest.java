package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptDraftRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizPackageResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StartAttemptRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.SubmitAttemptRequest;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.Quiz;
import com.erg.ergspringboot.lms.internal.attempt.LmsAttemptUseCase;
import com.erg.ergspringboot.lms.internal.resource.LmsResourceQueryUseCase;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.AssignmentResource;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Query economy of the student exam hot path: what autosave, start and the learner package no longer load. */
class LmsExamHotPathQueryEconomyTest {
    private LmsAttemptFixture fixture;
    private LmsAttemptUseCase useCase;
    private LmsResourceQueryUseCase resources;

    @BeforeEach
    void setUp() {
        fixture = new LmsAttemptFixture();
        useCase = fixture.useCase(Clock.fixed(Instant.parse("2026-08-08T09:00:00Z"), ZoneOffset.UTC));
        resources = new LmsResourceQueryUseCase(fixture.quizzes, fixture.packages, fixture.policy, fixture.permissions);
    }

    // Task 1: autosave / sync never use the assignment, so the owner path does not load it.

    @Test
    void autosaveByTheOwnerNeitherLoadsTheAssignmentNorFailsWithoutOne() {
        when(fixture.attempts.findByTenantIdAndId("erg", "att-1")).thenReturn(Optional.of(attempt("stu-1")));
        when(fixture.students.findFirstByTenantIdAndAuthUserId("erg", "usr-1")).thenReturn(Optional.of(student()));

        var saved = useCase.saveDraft("att-1", new AttemptDraftRequest(Map.of("q1", "A"), "hash-1", "1", List.of(), Map.of()),
                access("usr-1"));
        useCase.sync("att-1", null, access("usr-1"));

        assertThat(saved.answers()).containsKey("q1");
        verify(fixture.assignments, never()).findByTenantIdAndId(any(), any());
    }

    @Test
    void autosaveByANonOwnerIsRefusedBeforeAnyScopeLookup() {
        when(fixture.attempts.findByTenantIdAndId("erg", "att-1")).thenReturn(Optional.of(attempt("stu-1")));
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment()));

        assertThatThrownBy(() -> useCase.saveDraft("att-1",
                new AttemptDraftRequest(Map.of("q1", "A"), "hash-1", "1", List.of(), Map.of()), access("usr-other")))
                .isInstanceOf(ForbiddenException.class);

        verify(fixture.assignments, never()).findByTenantIdAndId(any(), any()); // only the owner may autosave
    }

    @Test
    void submitAlwaysReadsTheAssignmentFresh() {
        when(fixture.attempts.findByTenantIdAndId("erg", "att-1")).thenReturn(Optional.of(attempt("usr-1")));
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment()));

        useCase.submit("att-1", new SubmitAttemptRequest(Map.of("q1", "A"), null, "hash-1", "1", Map.of(), List.of()), access("usr-1"));

        verify(fixture.assignments, times(1)).findByTenantIdAndId("erg", "asg-1");
    }

    // Task 3: starting an attempt reads the cached start info, not the package_json or the quiz row.

    @Test
    void startUsesTheStartInfoProjectionAndLoadsNeitherQuizNorPackage() {
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment()));
        when(fixture.students.findFirstByTenantIdAndAuthUserId("erg", "usr-1")).thenReturn(Optional.of(student()));
        Quiz quiz = new Quiz();
        quiz.setKind("test");
        var pkg = new com.erg.ergspringboot.lcms.persistence.quiz.version.QuizPackage();
        pkg.setId("pkg-1");
        pkg.setQuizVersionId("ver-1");
        pkg.setContentHash("hash-1");
        pkg.setGradingMode("server-authoritative");
        when(fixture.packages.startInfo("erg", "quiz-1", null)).thenReturn(QuizStartInfo.of(quiz, pkg));

        var response = useCase.start("usr-1", new StartAttemptRequest("asg-1", "quiz-1", "pkg-1", "hash-1"), null, access("usr-1"));

        assertThat(response.packageId()).isEqualTo("pkg-1");
        assertThat(response.studentId()).isEqualTo("stu-1");
        assertThat(response.policySnapshot().quizVersionId()).isEqualTo("ver-1");
        verify(fixture.quizzes, never()).findByTenantIdAndId(any(), any());
        verify(fixture.packages, never()).activePackageEntity(any(), any());
        verify(fixture.packages, never()).packageEntityForVersion(any(), any(), any());
    }

    // Task 4: one exists query authorizes the learner package; the Quiz row is loaded only for the legacy fallback.

    @Test
    void learnerPackageWithoutAnAssignmentIsAuthorizedByOneExistsQuery() {
        when(fixture.students.findFirstByTenantIdAndAuthUserId("erg", "usr-1")).thenReturn(Optional.of(student()));
        when(fixture.assignments.existsVisibleQuizForLearner("erg", "quiz-1", null, "stu-1", "class-a", "usr-1")).thenReturn(true);
        when(fixture.packages.activePackage("erg", "quiz-1")).thenReturn(Optional.of(published()));

        QuizPackageResponse response = resources.getQuizPackage("quiz-1", null, access("usr-1"));

        assertThat(response.id()).isEqualTo("pkg-1");
        verify(fixture.assignments, times(1)).existsVisibleQuizForLearner(any(), any(), any(), any(), any(), any());
        verify(fixture.assignments, never()).findByTenantIdAndQuizId(any(), any());
        verify(fixture.assignments, never()).findByTenantIdAndIdIn(any(), any());
        verify(fixture.quizzes, never()).findByTenantIdAndId(any(), any());
    }

    @Test
    void learnerPackageOfAnAssignmentUsesTheSameExistsQueryNarrowedToThatAssignment() {
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment()));
        AssignmentResource resource = new AssignmentResource();
        resource.setAssignmentId("asg-1");
        resource.setQuizId("quiz-1");
        resource.setQuizVersionId("ver-1");
        when(fixture.resources.findByTenantIdAndAssignmentIdAndQuizId("erg", "asg-1", "quiz-1")).thenReturn(Optional.of(resource));
        when(fixture.assignments.existsVisibleQuizForLearner("erg", "quiz-1", "asg-1", null, null, "usr-1")).thenReturn(true);
        when(fixture.packages.packageForVersion("erg", "quiz-1", "ver-1")).thenReturn(Optional.of(published()));

        assertThat(resources.getQuizPackage("quiz-1", "asg-1", access("usr-1")).id()).isEqualTo("pkg-1");

        verify(fixture.recipients, never()).existsByTenantIdAndAssignmentId(any(), any());
        verify(fixture.quizzes, never()).findByTenantIdAndId(any(), any());
    }

    @Test
    void deniedLearnerGets403ForAKnownQuizAnd404ForAMissingOne() {
        when(fixture.quizzes.findByTenantIdAndId("erg", "quiz-1")).thenReturn(Optional.of(new Quiz()));

        assertThatThrownBy(() -> resources.getQuizPackage("quiz-1", null, access("usr-1"))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> resources.getQuizPackage("quiz-9", null, access("usr-1"))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void legacyFallbackLoadsTheQuizOnlyWhenNoPackageIsPublished() {
        when(fixture.students.findFirstByTenantIdAndAuthUserId("erg", "usr-1")).thenReturn(Optional.of(student()));
        when(fixture.assignments.existsVisibleQuizForLearner("erg", "quiz-1", null, "stu-1", "class-a", "usr-1")).thenReturn(true);
        when(fixture.packages.activePackage("erg", "quiz-1")).thenReturn(Optional.empty());
        Quiz quiz = new Quiz();
        quiz.setId("quiz-1");
        quiz.setTitle("Legacy");
        when(fixture.quizzes.findByTenantIdAndId("erg", "quiz-1")).thenReturn(Optional.of(quiz));

        assertThat(resources.getQuizPackage("quiz-1", null, access("usr-1")).quiz().quiz().title()).isEqualTo("Legacy");
    }

    // Task 2: the one student lookup.

    @Test
    void identityLookupReturnsASmallRecordAndNoneForAnUnlinkedLogin() {
        when(fixture.students.findFirstByTenantIdAndAuthUserId("erg", "usr-1")).thenReturn(Optional.of(student()));
        var lookup = new LmsStudentIdentityLookup(fixture.students);

        var found = lookup.byAuthUser("erg", "usr-1");
        var none = lookup.byAuthUser("erg", "usr-unlinked");

        assertThat(found.exists()).isTrue();
        assertThat(found.studentId()).isEqualTo("stu-1");
        assertThat(found.academicClassId()).isEqualTo("class-a");
        assertThat(none).isEqualTo(LmsStudentIdentityLookup.StudentIdentity.NONE);
        assertThat(none.exists()).isFalse();
        assertThat(lookup.byAuthUser("erg", " ")).isEqualTo(LmsStudentIdentityLookup.StudentIdentity.NONE);
        assertThat(fixture.policy.learner("erg", "usr-1")).get().satisfies(snapshot -> {
            assertThat(snapshot.getId()).isEqualTo("stu-1");
            assertThat(snapshot.getAcademicClassId()).isEqualTo("class-a");
        });
        assertThat(fixture.policy.learner("erg", "usr-unlinked")).isEmpty();
    }

    private Student student() {
        Student student = new Student();
        student.setId("stu-1");
        student.setAuthUserId("usr-1");
        student.setAcademicClassId("class-a");
        student.setFullName("Nguyen An");
        return student;
    }

    private Assignment assignment() {
        Assignment assignment = new Assignment();
        assignment.setId("asg-1");
        assignment.setTenantId("erg");
        assignment.setQuizId("quiz-1");
        assignment.setStatus("active");
        assignment.setAcademicClassId("class-a");
        return assignment;
    }

    private Attempt attempt(String studentId) {
        Attempt attempt = new Attempt();
        attempt.setId("att-1");
        attempt.setTenantId("erg");
        attempt.setAssignmentId("asg-1");
        attempt.setQuizId("quiz-1");
        attempt.setStudentId(studentId);
        attempt.setPackageHash("hash-1");
        attempt.setStatus("in_progress");
        attempt.setAnswers(new LinkedHashMap<>());
        attempt.setStartedAt(OffsetDateTime.parse("2026-08-08T08:50:00Z"));
        return attempt;
    }

    private QuizPackageResponse published() {
        return new QuizPackageResponse(1, "hash", "hash", "sig", "server-authoritative", null, Map.of(), "pkg-1");
    }

    private UserAccessContext access(String userId) {
        return new UserAccessContext(userId, "erg", List.of("*"), List.of("*"), null, null);
    }
}
