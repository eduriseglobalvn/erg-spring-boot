package com.erg.ergspringboot.lcms.quiz.authoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.Quiz;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizRepository;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.MethodAuthorizationEvaluator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class TeacherQuizOwnershipTest {
    private final MethodAuthorizationEvaluator authorization = mock(MethodAuthorizationEvaluator.class);
    private final QuizRepository quizzes = mock(QuizRepository.class);
    private final TeacherQuizOwnership ownership = new TeacherQuizOwnership(authorization, quizzes);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void signIn(String userId, boolean mayPublishSystemQuizzes) {
        ErgPrincipal principal = mock(ErgPrincipal.class);
        when(principal.userId()).thenReturn(userId);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authorization.isAllowedAny(authentication, "lms.quiz.publish", "*")).thenReturn(mayPublishSystemQuizzes);
    }

    private Quiz quiz(String portal, String owner) {
        Quiz quiz = new Quiz();
        quiz.setPortal(portal);
        quiz.setOwnerUserId(owner);
        when(quizzes.findByIdAndTenantId("quiz-1", "tenant-1")).thenReturn(Optional.of(quiz));
        return quiz;
    }

    @Test
    void retiringQuizzesClearsTheCachesOnlyWhenSomethingChanged() {
        var cacheManager = mock(org.springframework.cache.CacheManager.class);
        var cache = mock(org.springframework.cache.Cache.class);
        when(cacheManager.getCache(any())).thenReturn(cache);
        var job = new TeacherQuizOwnership(authorization, quizzes, (com.erg.ergspringboot.platform.events.ClusterJobLock) null, null, cacheManager);
        when(quizzes.softDeleteTeacherQuizzesOfLockedOwners(any())).thenReturn(0, 2);

        assertThat(job.retireQuizzesOfLockedOwners()).isZero();
        verify(cache, never()).clear();
        assertThat(job.retireQuizzesOfLockedOwners()).isEqualTo(2);
        verify(cache, org.mockito.Mockito.times(2)).clear();
    }

    @Test
    void aTeachersNewQuizBecomesTheirs() {
        signIn("teacher-1", false);
        Quiz quiz = quiz("lcms", "teacher-1");

        ownership.claim("tenant-1", "quiz-1");

        assertThat(quiz.getPortal()).isEqualTo("teacher");
        verify(quizzes).save(quiz);
    }

    @Test
    void theTrainingOfficeQuizStaysASystemQuiz() {
        signIn("pdt-1", true);

        ownership.claim("tenant-1", "quiz-1");

        verify(quizzes, never()).save(any());
    }

    @Test
    void aTeacherMayNotChangeAnotherTeachersQuizNorASystemQuiz() {
        signIn("teacher-1", false);
        quiz("teacher", "teacher-2");
        assertThatThrownBy(() -> ownership.requireOwn("tenant-1", "quiz-1")).isInstanceOf(ForbiddenException.class);
        quiz("lcms", "teacher-1");
        assertThatThrownBy(() -> ownership.requireOwn("tenant-1", "quiz-1")).isInstanceOf(ForbiddenException.class);
        quiz("teacher", "teacher-1");
        ownership.requireOwn("tenant-1", "quiz-1");
    }

    @Test
    void aTeacherMayReadASystemQuizOrTheirOwnButNotAnotherTeachersQuiz() {
        signIn("teacher-1", false);
        quiz("teacher", "teacher-2");
        assertThatThrownBy(() -> ownership.requireVisible("tenant-1", "quiz-1")).isInstanceOf(ResourceNotFoundException.class);
        quiz("lcms", "teacher-2");
        ownership.requireVisible("tenant-1", "quiz-1");
        quiz("teacher", "teacher-1");
        ownership.requireVisible("tenant-1", "quiz-1");
    }

    @Test
    void theTrainingOfficeMayChangeAnyQuiz() {
        signIn("pdt-1", true);

        ownership.requireOwn("tenant-1", "quiz-1");

        verify(quizzes, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void onlyATeacherGetsAViewerIdSoTheirQuizListsAreFilteredInSql() {
        signIn("teacher-1", false);
        assertThat(ownership.teacherViewerId()).isEqualTo("teacher-1");
        signIn("pdt-1", true);
        assertThat(ownership.teacherViewerId()).isNull();
    }
}
