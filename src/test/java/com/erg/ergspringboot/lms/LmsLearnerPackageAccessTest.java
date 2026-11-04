package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.ContentScope;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizDetailResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizPackageResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.lms.internal.resource.LmsResourceQueryUseCase;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.AssignmentResource;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** (a)/SQ-06/N3: learners only get packages of assigned quizzes, pinned version, without answer key. */
class LmsLearnerPackageAccessTest {
    private LmsAttemptFixture fixture;
    private LmsResourceQueryUseCase resources;
    private final UserAccessContext learner = new UserAccessContext("usr-1", "erg", List.of(), List.of(), null, null);

    @BeforeEach
    void setUp() {
        fixture = new LmsAttemptFixture();
        resources = new LmsResourceQueryUseCase(fixture.quizzes, fixture.packages, fixture.policy, fixture.permissions);
        Quiz quiz = new Quiz();
        quiz.setId("quiz-1");
        quiz.setTenantId("erg");
        when(fixture.quizzes.findByTenantIdAndId("erg", "quiz-1")).thenReturn(Optional.of(quiz));
    }

    @Test
    void learnerCannotLoadPackageOfUnassignedQuiz() {
        when(fixture.packages.activePackage("erg", "quiz-1")).thenReturn(Optional.of(packageWithKey()));

        assertThatThrownBy(() -> resources.getQuizPackage("quiz-1", null, learner)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assignedLearnerGetsPinnedVersionWithoutAnswerKey() {
        Assignment assignment = assignment(false);
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment));
        AssignmentResource resource = new AssignmentResource();
        resource.setAssignmentId("asg-1");
        resource.setQuizId("quiz-1");
        resource.setQuizVersionId("ver-1");
        when(fixture.resources.findByTenantIdAndAssignmentIdAndQuizId("erg", "asg-1", "quiz-1")).thenReturn(Optional.of(resource));
        when(fixture.packages.packageForVersion("erg", "quiz-1", "ver-1")).thenReturn(Optional.of(packageWithKey()));

        QuizPackageResponse response = resources.getQuizPackage("quiz-1", "asg-1", learner);

        assertThat(response.answerKey()).isEmpty();
    }

    @Test
    void answerKeyIsOnlyReturnedWhenAssignmentExplicitlyAllowsOfflineGrading() {
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment(true)));
        when(fixture.packages.activePackage("erg", "quiz-1")).thenReturn(Optional.of(packageWithKey()));

        assertThat(resources.getQuizPackage("quiz-1", "asg-1", learner).answerKey()).containsKey("q1");
    }

    @Test
    void authoringReaderKeepsTheActivePackageView() {
        when(fixture.permissions.hasAny("lms.quiz.read", "*")).thenReturn(true);
        when(fixture.packages.activePackage("erg", "quiz-1")).thenReturn(Optional.of(packageWithKey()));

        assertThat(resources.getQuizPackage("quiz-1", null, learner).answerKey()).containsKey("q1");
    }

    private Assignment assignment(boolean offline) {
        Assignment assignment = new Assignment();
        assignment.setId("asg-1");
        assignment.setTenantId("erg");
        assignment.setQuizId("quiz-1");
        assignment.setStatus("active");
        assignment.setStudentIds(List.of("usr-1"));
        assignment.setOfflineAllowed(offline);
        return assignment;
    }

    private QuizPackageResponse packageWithKey() {
        QuizResponse quiz = new QuizResponse("quiz-1", new ContentScope(null, null), "Quiz", null, null, null,
                List.of(), List.of(), "published", 1, "hash", null, null);
        return new QuizPackageResponse(1, "hash", "hash", "sig", "client-first",
                new QuizDetailResponse(quiz, List.of(), Map.of(), Map.of(), Map.of()), Map.of("q1", Map.of("correctChoiceId", "a")));
    }
}
